package interface_adapter.log_workout;

import java.util.List;
import java.util.UUID;

/**
 * {@code planId} is null before a workout has started. {@code error} is null when there is nothing to report.
 * {@code askDiscard} is true when the user finished with no sets recorded and should be asked to discard.
 */
public record LogWorkoutState(UUID planId, String planName, List<ExerciseRow> exercises, String error,
                              boolean askDiscard) {
    public LogWorkoutState {
        exercises = List.copyOf(exercises);
    }

    public static LogWorkoutState empty() {
        return new LogWorkoutState(null, "", List.of(), null, false);
    }

    public LogWorkoutState withError(String error) {
        return new LogWorkoutState(planId, planName, exercises, error, false);
    }

    public LogWorkoutState withDiscardPrompt() {
        return new LogWorkoutState(planId, planName, exercises, null, true);
    }

    /** {@code details} is the one line under the exercise name: body part, equipment, target and last time. */
    public record ExerciseRow(String exerciseId, String exerciseName, String details, List<SetRow> sets) {
        public ExerciseRow {
            sets = List.copyOf(sets);
        }
    }

    /**
     * One set row on screen. {@code previous} is last time's set at this position, or empty when there isn't one.
     * A null {@code weight} or {@code reps} is a blank cell.
     */
    public record SetRow(String previous, Double weight, Integer reps) {
    }
}
