package data_access;

import com.google.gson.JsonParser;
import entity.Follow;
import entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import use_case.account.AccountProfile;
import use_case.account.AuthSession;
import use_case.change_password.ChangePasswordUserDataAccessInterface.Result;
import use_case.following.FollowingDataAccessInterface.RequestFailed;
import use_case.following.FollowingDataAccessInterface.SaveResult;
import use_case.unfollow_account.UnfollowAccountDataAccessInterface.DeleteFailed;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SupabaseFollowingDaoTest {
    private static final User OWNER = new User(UUID.randomUUID(), "owner", "Owner");
    private static final User TARGET = new User(UUID.randomUUID(), "target", "Target");
    private static final UUID OWNER_ID = OWNER.getUserID();
    private static final UUID TARGET_ID = TARGET.getUserID();
    private static final Follow BOOKMARK = new Follow(OWNER_ID, TARGET_ID);
    private static final String DELETE_PATH = "/rest/v1/follows?follower_id=eq." + OWNER_ID
            + "&followed_id=eq." + TARGET_ID;
    private static final String TARGET_ROW = """
            [{"id":"%s","username":"target","display_name":"Target"}]
            """.formatted(TARGET_ID);

    private final InMemorySessionStore sessions = new InMemorySessionStore();
    private LoopbackSupabase server;
    private SupabaseClient client;
    private SupabaseFollowingDao dao;

    @BeforeEach
    void setUp() throws IOException {
        server = new LoopbackSupabase();
        client = server.client(Duration.ofSeconds(3));
        dao = new SupabaseFollowingDao(client, sessions);
        sessions.start(new AuthSession(OWNER_ID, "owner.token"), OWNER);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void looksUpAnAccountWithTheSessionTokenAndExactUsername() throws Exception {
        server.enqueue(200, TARGET_ROW);
        assertEquals(AccountProfile.from(TARGET), dao.findByUsername(OWNER_ID, "target").orElseThrow());
        assertRequest("POST", "/rest/v1/rpc/find_follow_profile", "owner.token", """
                {"target_username":"target"}
                """);
    }

    @Test
    void distinguishesAnUnknownUsernameFromLookupAndListFailures() throws Exception {
        server.enqueue(200, "[]");
        assertTrue(dao.findByUsername(OWNER_ID, "missing").isEmpty());
        server.enqueue(200, "[{}]");
        assertThrows(RequestFailed.class, () -> dao.findByUsername(OWNER_ID, "target"));
        server.enqueue(503, "[]");
        assertThrows(RequestFailed.class, () -> dao.list(OWNER_ID));
    }

    @Test
    void refusesUnexpectedLookupResults() {
        server.enqueue(200, TARGET_ROW.replace("target", "a_different_username"));
        assertThrows(RequestFailed.class, () -> dao.findByUsername(OWNER_ID, "target"));
        final var rows = JsonParser.parseString(TARGET_ROW).getAsJsonArray();
        rows.add(rows.get(0));
        server.enqueue(200, rows.toString());
        assertThrows(RequestFailed.class, () -> dao.findByUsername(OWNER_ID, "target"));
    }

    @Test
    void refusesTheListWhenAnyProfileIsMalformed() {
        for (String malformed : List.of(
                "{broken", "{}",
                TARGET_ROW.replace(TARGET_ID.toString(), "invalid-id"),
                TARGET_ROW.replace("\"target\"", "\"INVALID\""),
                TARGET_ROW.replace("\"Target\"", "\" \""))) {
            server.enqueue(200, malformed);
            assertThrows(RequestFailed.class, () -> dao.list(OWNER_ID));
        }
    }

    @Test
    void aRequestTimeoutBecomesARecoverableDaoFailure() {
        final var shortDeadline = new SupabaseFollowingDao(server.client(Duration.ofMillis(500)), sessions);
        server.stallBody(200, "[]");

        assertThrows(RequestFailed.class, () -> shortDeadline.list(OWNER_ID));
    }

    @Test
    void anInterruptedRequestPreservesTheThreadsInterruptStatus() {
        server.stallBody(200, "[]");
        Thread.currentThread().interrupt();

        try {
            assertThrows(RequestFailed.class, () -> dao.list(OWNER_ID));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void signedOutOperationsSendNoRequests() throws Exception {
        sessions.endCurrentSession();

        assertThrows(RequestFailed.class, () -> dao.list(OWNER_ID));
        assertThrows(RequestFailed.class, () -> dao.findByUsername(OWNER_ID, "target"));
        assertThrows(RequestFailed.class, () -> dao.save(BOOKMARK));
        assertThrows(DeleteFailed.class, () -> dao.delete(OWNER_ID, TARGET_ID));
        assertNull(server.unexpectedCall());
    }

    @Test
    void listsAsTheCurrentAccountAndRefusesRequestsForAnOldAccount() throws Exception {
        server.enqueue(200, TARGET_ROW);
        assertEquals(List.of(AccountProfile.from(TARGET)), dao.list(OWNER_ID));
        assertRequest("POST", "/rest/v1/rpc/list_following", "owner.token", "{}");

        sessions.endCurrentSession();
        sessions.start(new AuthSession(TARGET_ID, "target.token"), TARGET);
        assertThrows(RequestFailed.class, () -> dao.list(OWNER_ID));
        assertThrows(RequestFailed.class, () -> dao.findByUsername(OWNER_ID, "target"));
        assertThrows(RequestFailed.class, () -> dao.save(BOOKMARK));
        assertThrows(DeleteFailed.class, () -> dao.delete(OWNER_ID, TARGET_ID));
        assertNull(server.unexpectedCall());
        server.enqueue(200, "[]");
        assertTrue(dao.list(TARGET_ID).isEmpty());
        assertRequest("POST", "/rest/v1/rpc/list_following", "target.token", "{}");
    }

    @Test
    void sendsOnlyFollowedIdAndRecognizesOnlyUniqueKeyConflictsAsDuplicates() throws Exception {
        server.enqueue(201, "");
        assertEquals(SaveResult.SAVED, dao.save(BOOKMARK));
        assertRequest("POST", "/rest/v1/follows", "owner.token", "{\"followed_id\":\"" + TARGET_ID + "\"}");
        server.enqueue(409, "{\"code\":\"23505\"}");
        assertEquals(SaveResult.ALREADY_FOLLOWING, dao.save(BOOKMARK));
        server.enqueue(409, "{\"code\":\"23503\"}");
        assertThrows(RequestFailed.class, () -> dao.save(BOOKMARK));
        server.enqueue(503, "{\"code\":\"23505\"}");
        assertThrows(RequestFailed.class, () -> dao.save(BOOKMARK));
    }

    @Test
    void deletesByBothIdsAndTreatsAnEmptySuccessAsSafeToRetry() throws Exception {
        server.enqueue(503, "{}");
        server.enqueue(204, "");
        server.enqueue(204, "");

        assertThrows(DeleteFailed.class, () -> dao.delete(OWNER_ID, TARGET_ID));
        assertRequest("DELETE", DELETE_PATH, "owner.token", null);
        dao.delete(OWNER_ID, TARGET_ID);
        assertRequest("DELETE", DELETE_PATH, "owner.token", null);
        dao.delete(OWNER_ID, TARGET_ID);
        assertRequest("DELETE", DELETE_PATH, "owner.token", null);
    }

    @Test
    void timeoutIsRecoverableEvenWhenTheServerEventuallyRepliesWithSuccess() throws Exception {
        final var shortDeadline = new SupabaseFollowingDao(server.client(Duration.ofMillis(500)), sessions);
        server.enqueue(204, "", () -> LockSupport.parkNanos(Duration.ofSeconds(5).toNanos()));

        assertThrows(DeleteFailed.class, () -> shortDeadline.delete(OWNER_ID, TARGET_ID));
        assertRequest("DELETE", DELETE_PATH, "owner.token", null);
        server.enqueue(204, "");
        dao.delete(OWNER_ID, TARGET_ID);
        assertRequest("DELETE", DELETE_PATH, "owner.token", null);
    }

    @Test
    void interruptedDeletePreservesTheThreadsInterruptStatus() {
        server.enqueue(204, "");
        Thread.currentThread().interrupt();

        try {
            assertThrows(DeleteFailed.class, () -> dao.delete(OWNER_ID, TARGET_ID));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void followingUsesTheFreshSessionAfterAChangeOfPassword() throws Exception {
        final var passwordChange = new SupabaseChangePasswordDataAccessObject(client,
                new SupabaseAuthDataAccessObject(client), sessions);
        server.enqueue(200, "{\"email\":\"owner@example.test\"}");
        server.enqueue(200, """
                {"access_token":"fresh.token","user":{"id":"%s"}}
                """.formatted(OWNER_ID));
        server.enqueue(200, "{}");
        server.enqueue(200, TARGET_ROW);

        assertEquals(Result.CHANGED, passwordChange.changePassword("old password", "new password"));
        assertEquals(List.of(AccountProfile.from(TARGET)), dao.list(OWNER_ID));

        assertRequest("GET", "/auth/v1/user", "owner.token", null);
        assertRequest("POST", "/auth/v1/token?grant_type=password", null,
                "{\"email\":\"owner@example.test\",\"password\":\"old password\"}");
        assertRequest("PUT", "/auth/v1/user", "fresh.token", "{\"password\":\"new password\"}");
        assertRequest("POST", "/rest/v1/rpc/list_following", "fresh.token", "{}");
    }

    @Test
    void unfollowAndTheNextRefreshUseTheFreshSessionAfterAChangeOfPassword() throws Exception {
        final var passwordChange = new SupabaseChangePasswordDataAccessObject(client,
                new SupabaseAuthDataAccessObject(client), sessions);
        server.enqueue(200, "{\"email\":\"owner@example.test\"}");
        server.enqueue(200, """
                {"access_token":"fresh.token","user":{"id":"%s"}}
                """.formatted(OWNER_ID));
        server.enqueue(200, "{}");
        server.enqueue(204, "");
        server.enqueue(200, "[]");

        assertEquals(Result.CHANGED, passwordChange.changePassword("old password", "new password"));
        dao.delete(OWNER_ID, TARGET_ID);
        assertTrue(dao.list(OWNER_ID).isEmpty());

        assertRequest("GET", "/auth/v1/user", "owner.token", null);
        assertRequest("POST", "/auth/v1/token?grant_type=password", null,
                "{\"email\":\"owner@example.test\",\"password\":\"old password\"}");
        assertRequest("PUT", "/auth/v1/user", "fresh.token", "{\"password\":\"new password\"}");
        assertRequest("DELETE", DELETE_PATH, "fresh.token", null);
        assertRequest("POST", "/rest/v1/rpc/list_following", "fresh.token", "{}");
    }

    private void assertRequest(String method, String path, String token, String json) throws Exception {
        final var call = server.takeCall();
        assertEquals(method, call.method());
        assertEquals(path, call.path());
        assertEquals(LoopbackSupabase.API_KEY, call.apiKey());
        assertEquals(token == null ? null : "Bearer " + token, call.authorization());
        if ("DELETE".equals(method)) {
            assertEquals("return=minimal", call.prefer());
        }
        if (json == null) {
            assertEquals("", call.body());
        } else {
            assertEquals(JsonParser.parseString(json), JsonParser.parseString(call.body()));
        }
    }
}
