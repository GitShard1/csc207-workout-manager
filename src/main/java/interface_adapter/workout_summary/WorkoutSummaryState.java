package interface_adapter.workout_summary;

import java.util.List;

/** {@code personalRecords} is empty when the workout set none. */
public record WorkoutSummaryState(String planName, String date, int totalSets, List<ExerciseLine> exercises,
                                  List<String> personalRecords) {
    public WorkoutSummaryState {
        exercises = List.copyOf(exercises);
        personalRecords = List.copyOf(personalRecords);
    }

    public static WorkoutSummaryState empty() {
        return new WorkoutSummaryState("", "", 0, List.of(), List.of());
    }

    public record ExerciseLine(String exerciseName, String sets) {
    }
}
