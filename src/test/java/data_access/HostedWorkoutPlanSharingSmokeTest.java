package data_access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import use_case.account.AuthSession;
import use_case.login.SignInDataAccessInterface.SignedIn;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the deployed PostgREST contract with real signed-in accounts on a disposable project. */
@EnabledIfEnvironmentVariable(named = "WORKOUT_SHARING_SMOKE", matches = "1")
class HostedWorkoutPlanSharingSmokeTest {
    private static final String PLANS = "/rest/v1/workout_plans";
    private static final String RPC = "/rest/v1/rpc/";

    @Test
    void aRecipientCanCopyOneSharedPlanWithoutGainingDirectReadAccess() throws Exception {
        final SupabaseClient client = SupabaseClient.fromConfiguration(
                Map.of("SUPABASE_URL", setting("WORKOUT_SHARING_SMOKE_URL"),
                        "SUPABASE_PUBLISHABLE_KEY", setting("WORKOUT_SHARING_SMOKE_KEY")), new Properties());
        final SupabaseAuthDataAccessObject auth = new SupabaseAuthDataAccessObject(client);
        final AuthSession owner = signIn(auth, "OWNER");
        final AuthSession recipient = signIn(auth, "RECIPIENT");
        assertNotEquals(owner.userId(), recipient.userId(), "The smoke test requires two accounts.");
        final UUID sourceId = UUID.randomUUID();
        UUID copyId = null;
        try {
            seedPlan(client, owner, sourceId);
            assertDirectReadDenied(client, recipient, sourceId);

            final JsonObject target = new JsonObject();
            target.addProperty("plan_id", sourceId.toString());
            final JsonObject share = rpc(client, owner, "generate_workout_plan_share", target, "generated");
            final JsonObject code = new JsonObject();
            code.addProperty("share_code", share.get("code").getAsString());
            final JsonObject preview = rpc(client, recipient, "preview_shared_workout_plan", code, "available");
            assertEquals("Bench Press", preview.getAsJsonArray("exercises").get(0).getAsJsonObject()
                    .get("name").getAsString());

            final JsonObject request = code.deepCopy();
            request.addProperty("preview_revision", preview.get("revision").getAsString());
            request.addProperty("request_id", UUID.randomUUID().toString());
            final JsonObject saved = rpc(client, recipient, "copy_shared_workout_plan", request, "saved");
            copyId = UUID.fromString(saved.get("id").getAsString());
            assertNotEquals(sourceId, copyId);
            final JsonObject replay = rpc(client, recipient, "copy_shared_workout_plan", request, "saved");
            assertEquals(copyId.toString(), replay.get("id").getAsString());
            final HttpResponse<String> copy = client.get(planById(copyId), recipient.accessToken());
            assertEquals(200, copy.statusCode());
            final JsonArray copies = SupabaseJson.parse(copy.body()).getAsJsonArray();
            assertEquals(1, copies.size());
            assertEquals(recipient.userId().toString(), copies.get(0).getAsJsonObject().get("owner_id").getAsString());
            assertDirectReadDenied(client, recipient, sourceId);

            rpc(client, owner, "revoke_workout_plan_share", target, "revoked");
            rpc(client, recipient, "preview_shared_workout_plan", code, "unavailable");
        } finally {
            try {
                if (copyId != null) {
                    deletePlan(client, recipient, copyId);
                }
            } finally {
                deletePlan(client, owner, sourceId);
            }
        }
    }

    private static String setting(String name) {
        final String value = System.getenv(name);
        assertTrue(value != null && !value.isBlank(), () -> "Set " + name + " for the hosted smoke test.");
        return value;
    }

    private static AuthSession signIn(SupabaseAuthDataAccessObject auth, String role) {
        final String prefix = "WORKOUT_SHARING_SMOKE_" + role;
        final var result = auth.signIn(setting(prefix + "_EMAIL"), setting(prefix + "_PASSWORD"));
        assertTrue(result instanceof SignedIn, () -> "Check the disposable " + role.toLowerCase(java.util.Locale.ROOT)
                + " account credentials.");
        return ((SignedIn) result).session();
    }

    private static void seedPlan(SupabaseClient client, AuthSession owner, UUID sourceId) throws Exception {
        final JsonObject exercise = new JsonObject();
        exercise.addProperty("exercise_id", "smoke-bench");
        exercise.addProperty("name", "Bench Press");
        exercise.addProperty("body_part", "CHEST");
        exercise.addProperty("sets", 3);
        exercise.addProperty("reps", 8);
        exercise.addProperty("weight", 20);
        final JsonArray exercises = new JsonArray();
        exercises.add(exercise);
        final JsonObject source = new JsonObject();
        source.addProperty("id", sourceId.toString());
        source.addProperty("name", "Share smoke " + sourceId);
        source.add("exercises", exercises);
        assertEquals(201, client.post(PLANS, source, owner.accessToken()).statusCode());
    }

    private static String planById(UUID id) {
        return PLANS + "?id=eq." + id + "&select=id,owner_id";
    }

    private static void assertDirectReadDenied(SupabaseClient client, AuthSession viewer, UUID planId)
            throws Exception {
        final HttpResponse<String> response = client.get(planById(planId), viewer.accessToken());
        assertEquals(200, response.statusCode());
        assertTrue(SupabaseJson.parse(response.body()).getAsJsonArray().isEmpty());
    }

    private static JsonObject rpc(SupabaseClient client, AuthSession viewer, String action, JsonObject input,
                                  String expectedStatus) throws Exception {
        final HttpResponse<String> response = client.post(RPC + action, input, viewer.accessToken());
        assertEquals(200, response.statusCode(), action);
        final JsonObject result = SupabaseJson.object(SupabaseJson.parse(response.body()));
        assertNotNull(result, action);
        assertEquals(expectedStatus, SupabaseJson.string(result, "status"), action);
        return result;
    }

    private static void deletePlan(SupabaseClient client, AuthSession owner, UUID id) throws Exception {
        assertEquals(204, client.delete(PLANS + "?id=eq." + id, owner.accessToken()).statusCode());
    }
}
