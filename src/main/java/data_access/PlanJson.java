package data_access;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import entity.BodyPart;
import entity.Exercise;
import entity.PlannedExercise;
import entity.WorkoutPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Converts plans to and from the JSON stored in the {@code workout_plans} table. */
final class PlanJson {
    private PlanJson() { }

    static JsonArray exercisesToJson(List<PlannedExercise> plannedExercises) {
        final JsonArray exercises = new JsonArray();
        for (PlannedExercise plannedExercise : plannedExercises) {
            final Exercise exercise = plannedExercise.getExercise();
            final JsonObject json = new JsonObject();
            json.addProperty("exercise_id", exercise.getExerciseID());
            json.addProperty("name", exercise.getName());
            json.addProperty("description", exercise.getDescription());
            json.addProperty("body_part", exercise.getBodyPart().name());
            json.addProperty("target", exercise.getTarget());
            json.add("secondary_muscles", stringsToJson(exercise.getSecondaryMuscles()));
            json.addProperty("equipment", exercise.getEquipment());
            json.addProperty("mechanic", exercise.getMechanic());
            json.add("instructions", stringsToJson(exercise.getInstructions()));
            json.addProperty("sets", plannedExercise.getTargetSets());
            json.addProperty("reps", plannedExercise.getTargetReps());
            json.addProperty("weight", plannedExercise.getTargetWeight());
            exercises.add(json);
        }
        return exercises;
    }

    static WorkoutPlan planFromRow(JsonObject row) {
        if (row == null) {
            throw new IllegalArgumentException("The plan row is missing.");
        }
        final UUID id = requiredUuid(row, "id");
        final UUID ownerId = requiredUuid(row, "owner_id");
        final String name = requiredString(row, "name");
        final boolean workoutCompleted = requiredBoolean(row, "workout_completed");
        final boolean isPreset = requiredBoolean(row, "is_preset");
        final String copiedFrom = optionalString(row, "copied_from_user_id");
        final UUID copiedFromUserId = copiedFrom == null ? null : toUuid(copiedFrom, "copied_from_user_id");

        final JsonElement exercises = row.get("exercises");
        if (exercises == null || !exercises.isJsonArray()) {
            throw new IllegalArgumentException("exercises must be an array.");
        }
        final List<PlannedExercise> plannedExercises = new ArrayList<>();
        for (JsonElement element : exercises.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Each exercise must be an object.");
            }
            plannedExercises.add(plannedExerciseFrom(element.getAsJsonObject()));
        }
        return new WorkoutPlan(id, name, ownerId, plannedExercises, workoutCompleted, isPreset, copiedFromUserId);
    }

    private static PlannedExercise plannedExerciseFrom(JsonObject json) {
        final BodyPart bodyPart;
        try {
            bodyPart = BodyPart.valueOf(requiredString(json, "body_part"));
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("body_part is not a known body part.", exception);
        }
        final Exercise exercise = new Exercise(requiredString(json, "exercise_id"), requiredString(json, "name"),
                optionalString(json, "description"), bodyPart, optionalString(json, "target"),
                stringList(json, "secondary_muscles"), optionalString(json, "equipment"),
                optionalString(json, "mechanic"), stringList(json, "instructions"));
        return new PlannedExercise(exercise, requiredInt(json, "sets"), requiredInt(json, "reps"),
                optionalWeight(json, "weight"));
    }

    private static JsonArray stringsToJson(List<String> values) {
        final JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static String requiredString(JsonObject json, String property) {
        final String value = optionalString(json, property);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " must be a non-blank string.");
        }
        return value;
    }

    private static String optionalString(JsonObject json, String property) {
        final JsonElement value = json.get(property);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(property + " must be a string.");
        }
        return value.getAsString();
    }

    private static UUID requiredUuid(JsonObject json, String property) {
        return toUuid(requiredString(json, property), property);
    }

    private static UUID toUuid(String value, String property) {
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(property + " must be a UUID.", exception);
        }
    }

    private static boolean requiredBoolean(JsonObject json, String property) {
        final JsonElement value = json.get(property);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(property + " must be a boolean.");
        }
        return value.getAsBoolean();
    }

    private static int requiredInt(JsonObject json, String property) {
        final JsonElement value = json.get(property);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(property + " must be an integer.");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        }
        catch (ArithmeticException exception) {
            throw new IllegalArgumentException(property + " must be an integer.", exception);
        }
    }

    private static Double optionalWeight(JsonObject json, String property) {
        final JsonElement value = json.get(property);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(property + " must be a number.");
        }
        return value.getAsDouble();
    }

    private static List<String> stringList(JsonObject json, String property) {
        final JsonElement value = json.get(property);
        if (value == null || value.isJsonNull()) {
            return List.of();
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(property + " must be an array.");
        }
        final List<String> strings = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(property + " must contain only strings.");
            }
            strings.add(element.getAsString());
        }
        return strings;
    }
}
