package data_access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import entity.BodyPart;
import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import use_case.account.AuthSession;
import use_case.plan_common.PlanStorageUnavailableException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SupabasePlanDaoTest {
    private static final User OWNER = new User(UUID.randomUUID(), "owner", "Owner");
    private static final UUID OWNER_ID = OWNER.getUserID();
    private static final Exercise BENCH_PRESS = new Exercise("0025", "Barbell Bench Press", "A chest press.",
            BodyPart.CHEST, "Pectorals", List.of("Triceps"), "Barbell", "compound",
            List.of("Lie on the bench.", "Press the bar up."));
    private static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", null, BodyPart.UPPER_LEGS, "Quads",
            List.of(), "Barbell", "compound", List.of());
    private static final WorkoutPlan PUSH_DAY = new WorkoutPlan(UUID.randomUUID(), "Push Day", OWNER_ID,
            List.of(new PlannedExercise(BENCH_PRESS, 3, 8, 60.0), new PlannedExercise(SQUAT, 5, 5, null)),
            false, false, null);
    private static final String PLAN_PATH = "/rest/v1/workout_plans?select="
            + "id,owner_id,name,exercises,workout_completed,is_preset,copied_from_user_id&id=eq.";
    private static final String BASIC_EXERCISE =
            "{\"exercise_id\":\"0025\",\"name\":\"Barbell Bench Press\",\"body_part\":\"CHEST\",\"sets\":3,\"reps\":8}";

    private final InMemorySessionStore sessions = new InMemorySessionStore();
    private LoopbackSupabase server;
    private SupabasePlanDao dao;

    @BeforeEach
    void setUp() throws IOException {
        server = new LoopbackSupabase();
        dao = new SupabasePlanDao(server.client(Duration.ofSeconds(3)), sessions);
        sessions.start(new AuthSession(OWNER_ID, "owner.token"), OWNER);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void saveSendsOneRowTest() throws Exception {
        server.enqueue(201, "");

        dao.save(PUSH_DAY);

        final LoopbackSupabase.Call call = server.takeCall();
        assertEquals("POST", call.method());
        assertEquals("/rest/v1/workout_plans", call.path());
        assertEquals("Bearer owner.token", call.authorization());
        assertEquals("application/json", call.contentType());

        final JsonObject body = JsonParser.parseString(call.body()).getAsJsonObject();
        assertEquals(PUSH_DAY.getWorkoutPlanID().toString(), body.get("id").getAsString());
        assertEquals("Push Day", body.get("name").getAsString());
        assertFalse(body.get("workout_completed").getAsBoolean());
        assertTrue(body.get("copied_from_user_id").isJsonNull());
        assertFalse(body.has("owner_id"));
        assertFalse(body.has("is_preset"));

        final JsonArray exercises = body.getAsJsonArray("exercises");
        assertEquals(2, exercises.size());
        final JsonObject first = exercises.get(0).getAsJsonObject();
        assertEquals("0025", first.get("exercise_id").getAsString());
        assertEquals("CHEST", first.get("body_part").getAsString());
        assertEquals(List.of("Triceps"), first.getAsJsonArray("secondary_muscles").asList().stream()
                .map(element -> element.getAsString()).toList());
        assertEquals(2, first.getAsJsonArray("instructions").size());
        assertEquals(3, first.get("sets").getAsInt());
        assertEquals(8, first.get("reps").getAsInt());
        assertEquals(60.0, first.get("weight").getAsDouble());
        final JsonObject second = exercises.get(1).getAsJsonObject();
        assertTrue(second.get("weight").isJsonNull());
        assertTrue(second.get("description").isJsonNull());
    }

    @Test
    void saveFailureThrowsTest() {
        server.enqueue(409, "{\"code\":\"23505\"}");

        assertThrows(PlanStorageUnavailableException.class, () -> dao.save(PUSH_DAY));
    }

    @Test
    void saveWithoutSessionThrowsTest() throws Exception {
        sessions.endCurrentSession();

        assertThrows(PlanStorageUnavailableException.class, () -> dao.save(PUSH_DAY));
        assertNull(server.unexpectedCall());
    }

    @Test
    void roundTripTest() throws Exception {
        server.enqueue(201, "");
        dao.save(PUSH_DAY);
        final JsonObject row = JsonParser.parseString(server.takeCall().body()).getAsJsonObject();
        row.addProperty("owner_id", OWNER_ID.toString());
        row.addProperty("is_preset", false);
        server.enqueue(200, "[" + row + "]");

        final WorkoutPlan loaded = dao.getById(PUSH_DAY.getWorkoutPlanID());

        assertEquals(PUSH_DAY.getWorkoutPlanID(), loaded.getWorkoutPlanID());
        assertEquals("Push Day", loaded.getName());
        assertEquals(OWNER_ID, loaded.getUserOwnerID());
        assertFalse(loaded.isWorkoutCompleted());
        assertFalse(loaded.isPreset());
        assertNull(loaded.getCopiedFromUserID());
        assertEquals(2, loaded.getExercises().size());
        for (int i = 0; i < 2; i++) {
            final PlannedExercise expected = PUSH_DAY.getExercises().get(i);
            final PlannedExercise actual = loaded.getExercises().get(i);
            assertEquals(expected.getExercise().getExerciseID(), actual.getExercise().getExerciseID());
            assertEquals(expected.getExercise().getName(), actual.getExercise().getName());
            assertEquals(expected.getExercise().getDescription(), actual.getExercise().getDescription());
            assertEquals(expected.getExercise().getBodyPart(), actual.getExercise().getBodyPart());
            assertEquals(expected.getExercise().getTarget(), actual.getExercise().getTarget());
            assertEquals(expected.getExercise().getSecondaryMuscles(), actual.getExercise().getSecondaryMuscles());
            assertEquals(expected.getExercise().getEquipment(), actual.getExercise().getEquipment());
            assertEquals(expected.getExercise().getMechanic(), actual.getExercise().getMechanic());
            assertEquals(expected.getExercise().getInstructions(), actual.getExercise().getInstructions());
            assertEquals(expected.getTargetSets(), actual.getTargetSets());
            assertEquals(expected.getTargetReps(), actual.getTargetReps());
            assertEquals(expected.getTargetWeight(), actual.getTargetWeight());
        }
    }

    @Test
    void getByIdRequestTest() throws Exception {
        server.enqueue(200, "[]");

        assertNull(dao.getById(PUSH_DAY.getWorkoutPlanID()));

        final LoopbackSupabase.Call call = server.takeCall();
        assertEquals("GET", call.method());
        assertEquals(PLAN_PATH + PUSH_DAY.getWorkoutPlanID(), call.path());
        assertEquals("Bearer owner.token", call.authorization());
    }

    @Test
    void getByIdMissingListsBecomeEmptyTest() throws Exception {
        server.enqueue(200, "[" + row("\"" + PUSH_DAY.getWorkoutPlanID() + "\"", "[" + BASIC_EXERCISE + "]") + "]");

        final Exercise exercise = dao.getById(PUSH_DAY.getWorkoutPlanID()).getExercises().get(0).getExercise();

        assertTrue(exercise.getSecondaryMuscles().isEmpty());
        assertTrue(exercise.getInstructions().isEmpty());
    }

    @Test
    void getByIdServerErrorThrowsTest() {
        server.enqueue(500, "");

        assertThrows(PlanStorageUnavailableException.class, () -> dao.getById(PUSH_DAY.getWorkoutPlanID()));
    }

    @Test
    void getByIdMalformedRowThrowsTest() {
        server.enqueue(200, "[" + row("\"not-a-uuid\"", "[" + BASIC_EXERCISE + "]") + "]");

        assertThrows(PlanStorageUnavailableException.class, () -> dao.getById(PUSH_DAY.getWorkoutPlanID()));
    }

    @Test
    void getByIdUnknownBodyPartThrowsTest() {
        server.enqueue(200, "[" + row("\"" + PUSH_DAY.getWorkoutPlanID() + "\"",
                "[" + BASIC_EXERCISE.replace("CHEST", "ELBOWS") + "]") + "]");

        assertThrows(PlanStorageUnavailableException.class, () -> dao.getById(PUSH_DAY.getWorkoutPlanID()));
    }

    @Test
    void existsByNameTest() throws Exception {
        server.enqueue(200, "true");

        assertTrue(dao.existsByName(OWNER_ID, "Push Day"));

        final LoopbackSupabase.Call call = server.takeCall();
        assertEquals("POST", call.method());
        assertEquals("/rest/v1/rpc/plan_name_taken", call.path());
        assertEquals("Bearer owner.token", call.authorization());
        assertEquals(JsonParser.parseString("{\"plan_name\":\"Push Day\"}"), JsonParser.parseString(call.body()));

        server.enqueue(200, "false");

        assertFalse(dao.existsByName(OWNER_ID, "Push Day"));
    }

    @Test
    void existsByNameFailureThrowsTest() {
        server.enqueue(401, "{\"message\":\"JWT expired\"}");

        assertThrows(PlanStorageUnavailableException.class, () -> dao.existsByName(OWNER_ID, "Push Day"));
    }

    @Test
    void existsByNameForAnotherAccountThrowsTest() throws Exception {
        assertThrows(PlanStorageUnavailableException.class, () -> dao.existsByName(UUID.randomUUID(), "Push Day"));
        assertNull(server.unexpectedCall());
    }

    private static String row(String quotedId, String exercisesJson) {
        return "{\"id\":" + quotedId + ",\"owner_id\":\"" + OWNER_ID + "\",\"name\":\"Push Day\",\"exercises\":"
                + exercisesJson + ",\"workout_completed\":false,\"is_preset\":false,\"copied_from_user_id\":null}";
    }
}
