package data_access;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import use_case.account.AuthSession;
import use_case.plan_sharing.PlanSharingException;
import use_case.plan_sharing.PlanSharingGateway;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static use_case.plan_sharing.PlanSharingException.Reason.DELETED;
import static use_case.plan_sharing.PlanSharingException.Reason.REQUEST_FAILED;
import static use_case.plan_sharing.PlanSharingException.Reason.SIGN_IN_REQUIRED;
import static use_case.plan_sharing.PlanSharingException.Reason.STALE;
import static use_case.plan_sharing.PlanSharingException.Reason.UNAVAILABLE;

final class SupabasePlanSharingDaoTest {
    private static final User OWNER = new User(UUID.randomUUID(), "owner", "Owner");
    private static final User RECIPIENT = new User(UUID.randomUUID(), "recipient", "Recipient");
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID COPY_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String CODE = "abcda001-0002-4003-8004-000000000005";
    private static final String REVISION = "a".repeat(64);
    private static final Instant EXPIRY = Instant.parse("2026-12-01T00:00:00Z");
    private static final String EXPIRY_JSON = "2026-12-01T00:00:00+00:00";
    private final InMemorySessionStore session = new InMemorySessionStore();
    private LoopbackSupabase server;
    private SupabasePlanSharingDao dao;

    @BeforeEach
    void setUp() throws IOException {
        server = new LoopbackSupabase();
        dao = new SupabasePlanSharingDao(server.client(Duration.ofSeconds(3)), session);
        signIn(OWNER, "owner.token");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void listsGeneratesAndRevokesAsThePlanOwner() throws Exception {
        server.enqueue(200, """
                [{"id":"%s","name":"Push","share_expires_at":null},
                 {"id":"%s","name":"Legs","share_expires_at":"%s"}]
                """.formatted(PLAN_ID, COPY_ID, EXPIRY_JSON));
        server.enqueue(200, """
                {"status":"generated","code":"%s","expires_at":"%s"}
                """.formatted(CODE, EXPIRY_JSON));
        server.enqueue(200, "{\"status\":\"revoked\"}");

        assertEquals(List.of(new PlanSharingGateway.PlanSummary(PLAN_ID, "Push", null),
                new PlanSharingGateway.PlanSummary(COPY_ID, "Legs", EXPIRY)), dao.list(OWNER.getUserID()));
        assertEquals(new PlanSharingGateway.GeneratedCode(CODE, EXPIRY), dao.generate(OWNER.getUserID(), PLAN_ID));
        dao.revoke(OWNER.getUserID(), PLAN_ID);

        assertEquals(new JsonObject(), expectCall("list_owned_workout_plans", "owner.token"));
        assertEquals(PLAN_ID.toString(), expectCall("generate_workout_plan_share", "owner.token")
                .get("plan_id").getAsString());
        assertEquals(PLAN_ID.toString(), expectCall("revoke_workout_plan_share", "owner.token")
                .get("plan_id").getAsString());
    }

    @Test
    void previewsAndCopiesWithTheRecipientToken() throws Exception {
        signIn(RECIPIENT, "recipient.token");
        server.enqueue(200, """
                {"status":"available","name":"Push","revision":"%s","exercises":[
                  {"name":"Bench","sets":3,"reps":8,"weight":60},
                  {"name":"Push-up","sets":2,"reps":10,"weight":null}
                ]}
                """.formatted(REVISION));
        server.enqueue(200, "{\"status\":\"saved\",\"id\":\"" + COPY_ID + "\",\"name\":\"Push (copy)\"}");

        final PlanSharingGateway.Preview preview = dao.preview(RECIPIENT.getUserID(), CODE);
        assertEquals(new PlanSharingGateway.Preview("Push", List.of(
                new PlanSharingGateway.ExerciseTarget("Bench", 3, 8, 60.0),
                new PlanSharingGateway.ExerciseTarget("Push-up", 2, 10, null)), REVISION), preview);
        assertEquals(new PlanSharingGateway.CopyResult(COPY_ID, "Push (copy)"),
                dao.copy(RECIPIENT.getUserID(), CODE, preview.revision(), REQUEST_ID));

        final JsonObject lookup = expectCall("preview_shared_workout_plan", "recipient.token");
        assertEquals(CODE, lookup.get("share_code").getAsString());
        assertEquals(1, lookup.size());
        final JsonObject copy = expectCall("copy_shared_workout_plan", "recipient.token");
        assertEquals(CODE, copy.get("share_code").getAsString());
        assertEquals(REVISION, copy.get("preview_revision").getAsString());
        assertEquals(REQUEST_ID.toString(), copy.get("request_id").getAsString());
        assertEquals(3, copy.size());
    }

    @Test
    void rejectsPriorAccountRequestsWithoutUsingTheCurrentSessionsToken() throws Exception {
        signIn(RECIPIENT, "recipient.token");

        assertEquals(SIGN_IN_REQUIRED,
                assertThrows(PlanSharingException.class, () -> dao.list(OWNER.getUserID())).reason());
        assertEquals(SIGN_IN_REQUIRED, assertThrows(PlanSharingException.class,
                () -> dao.copy(OWNER.getUserID(), CODE, REVISION, REQUEST_ID)).reason());
        assertNull(server.unexpectedCall());
    }

    @Test
    void mapsStorageOutcomesAndUnexpectedResponses() {
        server.enqueue(200, "{\"status\":\"unavailable\"}");
        server.enqueue(200, "{\"status\":\"stale\"}");
        server.enqueue(200, "{\"status\":\"deleted\"}");
        server.enqueue(200, "{}");
        server.enqueue(200, "null");
        server.enqueue(401, "{}");
        server.enqueue(403, "{}");
        server.enqueue(429, "{}");
        server.enqueue(503, "{}");

        assertEquals(UNAVAILABLE,
                assertThrows(PlanSharingException.class, () -> dao.preview(OWNER.getUserID(), CODE)).reason());
        assertEquals(STALE, copyFailure());
        assertEquals(DELETED, copyFailure());
        assertEquals(REQUEST_FAILED, copyFailure());
        assertEquals(REQUEST_FAILED, copyFailure());
        assertEquals(SIGN_IN_REQUIRED, copyFailure());
        assertEquals(REQUEST_FAILED, copyFailure());
        assertEquals(REQUEST_FAILED, copyFailure());
        assertEquals(REQUEST_FAILED, copyFailure());
    }

    @Test
    void rejectsPreviewWithInvalidExerciseTargets() {
        server.enqueue(200, """
                {"status":"available","name":"Push","revision":"%s","exercises":[
                  {"name":"Bench","sets":3.5,"reps":8,"weight":60}
                ]}
                """.formatted(REVISION));

        assertEquals(REQUEST_FAILED,
                assertThrows(PlanSharingException.class, () -> dao.preview(OWNER.getUserID(), CODE)).reason());
    }

    private PlanSharingException.Reason copyFailure() {
        return assertThrows(PlanSharingException.class,
                () -> dao.copy(OWNER.getUserID(), CODE, REVISION, REQUEST_ID)).reason();
    }

    private void signIn(User user, String token) {
        session.start(new AuthSession(user.getUserID(), token), user);
    }

    private JsonObject expectCall(String rpc, String token) throws InterruptedException {
        final LoopbackSupabase.Call call = server.takeCall();
        assertEquals("POST", call.method());
        assertEquals("/rest/v1/rpc/" + rpc, call.path());
        assertEquals("Bearer " + token, call.authorization());
        return JsonParser.parseString(call.body()).getAsJsonObject();
    }
}
