package use_case.plan_common;

import entity.Exercise;
import entity.PlannedExercise;

import java.util.ArrayList;
import java.util.List;

public final class PlannedExerciseBuilder {
    private PlannedExerciseBuilder() {
    }

    /** The built exercises, or an error and no exercises when a row's exercise could not be found. */
    public record Result(List<PlannedExercise> exercises, String error) {
    }

    /**
     Looks up each row's exercise, in order, and builds its planned exercise. Stops at the first row whose exercise
     is not found and returns its error.
     */
    public static Result build(List<? extends PlanRow> rows, ExerciseLookupDataAccessInterface exerciseLookup) {
        final ArrayList<PlannedExercise> plannedExercises = new ArrayList<>();
        int row = 0;
        for (PlanRow planRow : rows) {
            row += 1;
            final Exercise exercise = exerciseLookup.findById(planRow.exerciseId());
            if (exercise == null) {
                return new Result(List.of(), "Row " + row + ": exercise not found.");
            }
            plannedExercises.add(new PlannedExercise(exercise, planRow.sets(), planRow.reps(), planRow.weight()));
        }
        return new Result(plannedExercises, "");
    }
}
