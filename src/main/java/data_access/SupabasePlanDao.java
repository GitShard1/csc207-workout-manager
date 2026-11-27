package data_access;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import entity.WorkoutPlan;
import use_case.create_plan.CreatePlanDataAccessInterface;
import use_case.open_plan.OpenPlanDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static data_access.SupabaseJson.object;
import static data_access.SupabaseJson.parse;

/** Stores plans in the {@code workout_plans} table, one row per plan with its exercises copied into it. */
public final class SupabasePlanDao implements CreatePlanDataAccessInterface, OpenPlanDataAccessInterface {
    private static final String COLUMNS = "id,owner_id,name,exercises,workout_completed,is_preset,copied_from_user_id";

    private final SupabaseClient client;
    private final InMemorySessionStore session;

    public SupabasePlanDao(SupabaseClient client, InMemorySessionStore session) {
        this.client = client;
        this.session = session;
    }

    @Override
    public WorkoutPlan getById(UUID planId) throws PlanStorageUnavailableException {
        final String token = session.accessToken().orElseThrow(SupabasePlanDao::noSession);
        final String path = "/rest/v1/workout_plans?select=" + COLUMNS + "&id=eq." + planId;
        final HttpResponse<String> response = send(() -> client.get(path, token));
        final JsonElement rows = parse(response.body());
        if (response.statusCode() != 200 || rows == null || !rows.isJsonArray() || rows.getAsJsonArray().size() > 1) {
            throw new PlanStorageUnavailableException("The plan could not be loaded.", null);
        }
        final JsonArray plans = rows.getAsJsonArray();
        if (plans.isEmpty()) {
            return null;
        }
        try {
            return PlanJson.planFromRow(object(plans.get(0)));
        }
        catch (IllegalArgumentException exception) {
            throw new PlanStorageUnavailableException("A stored plan could not be read.", exception);
        }
    }

    @Override
    public boolean existsByName(UUID ownerId, String planName) throws PlanStorageUnavailableException {
        final String token = session.accessTokenFor(ownerId).orElseThrow(SupabasePlanDao::noSession);
        final JsonObject body = new JsonObject();
        body.addProperty("plan_name", planName);
        final HttpResponse<String> response = send(() -> client.post("/rest/v1/rpc/plan_name_taken", body, token));
        final JsonElement taken = parse(response.body());
        if (response.statusCode() != 200 || taken == null || !taken.isJsonPrimitive()
                || !taken.getAsJsonPrimitive().isBoolean()) {
            throw new PlanStorageUnavailableException("The plan name could not be checked.", null);
        }
        return taken.getAsBoolean();
    }

    @Override
    public void save(WorkoutPlan workoutPlan) throws PlanStorageUnavailableException {
        final String token = session.accessTokenFor(workoutPlan.getUserOwnerID())
                .orElseThrow(SupabasePlanDao::noSession);
        final JsonObject body = new JsonObject();
        body.addProperty("id", workoutPlan.getWorkoutPlanID().toString());
        body.addProperty("name", workoutPlan.getName());
        body.add("exercises", PlanJson.exercisesToJson(workoutPlan.getExercises()));
        body.addProperty("workout_completed", workoutPlan.isWorkoutCompleted());
        final UUID copiedFrom = workoutPlan.getCopiedFromUserID();
        body.addProperty("copied_from_user_id", copiedFrom == null ? null : copiedFrom.toString());
        final HttpResponse<String> response = send(() -> client.post("/rest/v1/workout_plans", body, token));
        if (response.statusCode() != 201) {
            throw new PlanStorageUnavailableException("The plan could not be saved.", null);
        }
    }

    private interface SupabaseRequest {
        HttpResponse<String> send() throws IOException, InterruptedException;
    }

    private static HttpResponse<String> send(SupabaseRequest request) throws PlanStorageUnavailableException {
        try {
            return request.send();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlanStorageUnavailableException("Plans could not be reached.", exception);
        }
        catch (IOException exception) {
            throw new PlanStorageUnavailableException("Plans could not be reached.", exception);
        }
    }

    private static PlanStorageUnavailableException noSession() {
        return new PlanStorageUnavailableException("No signed-in session.", null);
    }
}
