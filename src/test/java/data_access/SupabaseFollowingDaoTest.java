package data_access;

import com.google.gson.JsonParser;
import entity.User;
import interface_adapter.following.FollowingPresenter;
import interface_adapter.following.FollowingState;
import interface_adapter.following.FollowingViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import use_case.account.AccountProfile;
import use_case.account.AuthSession;
import use_case.following.FollowingDataAccessInterface.RequestFailed;
import use_case.following.FollowingDataAccessInterface.SaveResult;
import use_case.following.FollowingInteractor;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SupabaseFollowingDaoTest {
    private static final User OWNER = new User(UUID.randomUUID(), "owner", "Owner");
    private static final User TARGET = new User(UUID.randomUUID(), "target", "Target");
    private static final UUID OWNER_ID = OWNER.getUserID();
    private static final UUID TARGET_ID = TARGET.getUserID();
    private static final String TARGET_ROW = """
            [{"id":"%s","username":"target","display_name":"Target"}]
            """.formatted(TARGET_ID);

    private final InMemorySessionStore sessions = new InMemorySessionStore();
    private LoopbackSupabase server;
    private SupabaseFollowingDao dao;

    @BeforeEach
    void setUp() throws IOException {
        server = new LoopbackSupabase();
        dao = new SupabaseFollowingDao(server.client(Duration.ofSeconds(3)), sessions);
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
        assertRequest("/rest/v1/rpc/find_follow_profile", "owner.token", """
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ab", "Alex", "alex smith", "abcdefghijklmnopqrstuvwxyz"})
    void invalidUsernamesAreRejectedWithoutARequest(String username) throws Exception {
        final FollowingViewModel viewModel = new FollowingViewModel();
        final FollowingInteractor interactor =
                new FollowingInteractor(sessions, dao, new FollowingPresenter(viewModel));

        interactor.findAccount(username);

        assertEquals("No account found for that username.", viewModel.getState().message());
        assertNull(server.unexpectedCall());
    }

    @Test
    void signedOutOperationsClearPreviouslyVisibleProfilesAndSendNoRequests() throws Exception {
        final FollowingViewModel viewModel = new FollowingViewModel();
        final FollowingInteractor interactor =
                new FollowingInteractor(sessions, dao, new FollowingPresenter(viewModel));
        final AccountProfile target = AccountProfile.from(TARGET);
        final FollowingState stale = new FollowingState(target, List.of(target), "");
        final List<Runnable> operations = List.of(interactor::loadFollowing, () -> interactor.findAccount("target"),
                () -> interactor.follow(target));
        sessions.endCurrentSession();

        for (Runnable operation : operations) {
            viewModel.setState(stale);
            operation.run();
            assertEquals(new FollowingState(null, List.of(), "Sign in to use Following."), viewModel.getState());
        }
        assertNull(server.unexpectedCall());
    }

    @Test
    void listsAsTheCurrentAccountAndRefusesRequestsForAnOldAccount() throws Exception {
        server.enqueue(200, TARGET_ROW);
        assertEquals(List.of(AccountProfile.from(TARGET)), dao.list(OWNER_ID));
        assertRequest("/rest/v1/rpc/list_following", "owner.token", "{}");

        sessions.endCurrentSession();
        sessions.start(new AuthSession(TARGET_ID, "target.token"), TARGET);
        assertThrows(RequestFailed.class, () -> dao.list(OWNER_ID));
        assertThrows(RequestFailed.class, () -> dao.save(OWNER_ID, TARGET_ID));
        assertNull(server.unexpectedCall());
        server.enqueue(200, "[]");
        assertTrue(dao.list(TARGET_ID).isEmpty());
        assertRequest("/rest/v1/rpc/list_following", "target.token", "{}");
    }

    @Test
    void sendsOnlyFollowedIdAndRecognizesOnlyUniqueKeyConflictsAsDuplicates() throws Exception {
        server.enqueue(201, "");
        assertEquals(SaveResult.SAVED, dao.save(OWNER_ID, TARGET_ID));
        assertRequest("/rest/v1/follows", "owner.token", "{\"followed_id\":\"" + TARGET_ID + "\"}");
        server.enqueue(409, "{\"code\":\"23505\"}");
        assertEquals(SaveResult.ALREADY_FOLLOWING, dao.save(OWNER_ID, TARGET_ID));
        server.enqueue(409, "{\"code\":\"23503\"}");
        assertThrows(RequestFailed.class, () -> dao.save(OWNER_ID, TARGET_ID));
        server.enqueue(503, "{\"code\":\"23505\"}");
        assertThrows(RequestFailed.class, () -> dao.save(OWNER_ID, TARGET_ID));
    }

    private void assertRequest(String path, String token, String json) throws Exception {
        final var call = server.takeCall();
        assertEquals("POST", call.method());
        assertEquals(path, call.path());
        assertEquals(LoopbackSupabase.API_KEY, call.apiKey());
        assertEquals("Bearer " + token, call.authorization());
        assertEquals(JsonParser.parseString(json), JsonParser.parseString(call.body()));
    }
}
