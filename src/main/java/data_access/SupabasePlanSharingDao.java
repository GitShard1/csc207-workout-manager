package data_access;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import use_case.plan_sharing.PlanSharingException;
import use_case.plan_sharing.PlanSharingGateway;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static data_access.SupabaseJson.id;
import static data_access.SupabaseJson.object;
import static data_access.SupabaseJson.parse;
import static data_access.SupabaseJson.string;
import static use_case.plan_sharing.PlanSharingException.Reason.DELETED;
import static use_case.plan_sharing.PlanSharingException.Reason.REQUEST_FAILED;
import static use_case.plan_sharing.PlanSharingException.Reason.SIGN_IN_REQUIRED;
import static use_case.plan_sharing.PlanSharingException.Reason.STALE;
import static use_case.plan_sharing.PlanSharingException.Reason.UNAVAILABLE;

/** Sends each sharing request with the token of the account that started the action. */
public final class SupabasePlanSharingDao implements PlanSharingGateway {
    private final SupabaseClient client;
    private final InMemorySessionStore session;

    public SupabasePlanSharingDao(SupabaseClient client, InMemorySessionStore session) {
        this.client = client;
        this.session = session;
    }

    @Override
    public List<PlanSummary> list(UUID viewer) throws PlanSharingException {
        final JsonElement response = post(viewer, "list_owned_workout_plans", new JsonObject());
        if (!response.isJsonArray()) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        final List<PlanSummary> plans = new ArrayList<>();
        for (JsonElement element : response.getAsJsonArray()) {
            final JsonObject row = object(element);
            final String name = requiredString(row, "name");
            final Instant expiresAt = nullableInstant(row, "share_expires_at");
            plans.add(new PlanSummary(requiredId(row), name, expiresAt));
        }
        return List.copyOf(plans);
    }

    @Override
    public GeneratedCode generate(UUID viewer, UUID planId) throws PlanSharingException {
        final JsonObject row = outcome(viewer, "generate_workout_plan_share", withPlanId(planId), "generated");
        final Instant expiresAt = nullableInstant(row, "expires_at");
        if (expiresAt == null) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        return new GeneratedCode(requiredString(row, "code"), expiresAt);
    }

    @Override
    public void revoke(UUID viewer, UUID planId) throws PlanSharingException {
        outcome(viewer, "revoke_workout_plan_share", withPlanId(planId), "revoked");
    }

    @Override
    public Preview preview(UUID viewer, String code) throws PlanSharingException {
        final JsonObject row = outcome(viewer, "preview_shared_workout_plan", withCode(code), "available");
        final JsonElement exerciseRows = row.get("exercises");
        if (exerciseRows == null || !exerciseRows.isJsonArray() || exerciseRows.getAsJsonArray().isEmpty()) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        final List<ExerciseTarget> exercises = new ArrayList<>();
        for (JsonElement element : exerciseRows.getAsJsonArray()) {
            final JsonObject exercise = object(element);
            final String name = requiredString(exercise, "name");
            exercises.add(new ExerciseTarget(name, positiveInt(exercise, "sets"), positiveInt(exercise, "reps"),
                    nullableWeight(exercise)));
        }
        return new Preview(requiredString(row, "name"), exercises, requiredString(row, "revision"));
    }

    @Override
    public CopyResult copy(UUID viewer, String code, String revision, UUID requestId) throws PlanSharingException {
        final JsonObject body = withCode(code);
        body.addProperty("preview_revision", revision);
        body.addProperty("request_id", requestId.toString());
        final JsonObject row = outcome(viewer, "copy_shared_workout_plan", body, "saved");
        return new CopyResult(requiredId(row), requiredString(row, "name"));
    }

    private JsonObject outcome(UUID viewer, String operation, JsonObject body, String success)
            throws PlanSharingException {
        final JsonObject row = object(post(viewer, operation, body));
        final String status = requiredString(row, "status");
        if (!success.equals(status)) {
            throw new PlanSharingException(switch (status) {
                case "unavailable" -> UNAVAILABLE;
                case "stale" -> STALE;
                case "deleted" -> DELETED;
                default -> REQUEST_FAILED;
            });
        }
        return row;
    }

    private JsonElement post(UUID viewer, String operation, JsonObject body) throws PlanSharingException {
        final String token = session.accessTokenFor(viewer)
                .orElseThrow(() -> new PlanSharingException(SIGN_IN_REQUIRED));
        final HttpResponse<String> response;
        try {
            response = client.post("/rest/v1/rpc/" + operation, body, token);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw new PlanSharingException(REQUEST_FAILED);
        } catch (IOException ignored) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        if (response.statusCode() == 401) {
            throw new PlanSharingException(SIGN_IN_REQUIRED);
        }
        final JsonElement value = parse(response.body());
        if (response.statusCode() != 200 || value == null || value.isJsonNull()) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        return value;
    }

    private static JsonObject withPlanId(UUID planId) {
        final JsonObject body = new JsonObject();
        body.addProperty("plan_id", planId.toString());
        return body;
    }

    private static JsonObject withCode(String code) {
        final JsonObject body = new JsonObject();
        body.addProperty("share_code", code);
        return body;
    }

    private static UUID requiredId(JsonObject row) throws PlanSharingException {
        final UUID value = id(row);
        if (value == null) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        return value;
    }

    private static String requiredString(JsonObject row, String key) throws PlanSharingException {
        final String value = string(row, key);
        if (value == null || value.isBlank()) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        return value;
    }

    private static Instant nullableInstant(JsonObject row, String key) throws PlanSharingException {
        final JsonElement value = row.get(key);
        if (value != null && value.isJsonNull()) {
            return null;
        }
        try {
            return Instant.parse(requiredString(row, key));
        } catch (DateTimeParseException ignored) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
    }

    private static int positiveInt(JsonObject row, String key) throws PlanSharingException {
        final JsonElement value = row.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        try {
            final int number = value.getAsBigDecimal().intValueExact();
            if (number > 0) {
                return number;
            }
        } catch (ArithmeticException | NumberFormatException ignored) {
            throw new PlanSharingException(REQUEST_FAILED);
        }
        throw new PlanSharingException(REQUEST_FAILED);
    }

    private static Double nullableWeight(JsonObject row) throws PlanSharingException {
        final JsonElement value = row.get("weight");
        if (value != null && value.isJsonNull()) {
            return null;
        }
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            final double weight = value.getAsDouble();
            if (Double.isFinite(weight) && weight >= 0) {
                return weight;
            }
        }
        throw new PlanSharingException(REQUEST_FAILED);
    }
}
