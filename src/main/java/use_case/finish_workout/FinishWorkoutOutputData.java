package use_case.finish_workout;

import use_case.workout_common.SetEntry;

import java.time.LocalDate;
import java.util.List;

/** {@code exercises} leaves out skipped exercises. {@code personalRecords} is empty when none were set. */
public record FinishWorkoutOutputData(String planName, LocalDate date, List<ExerciseSummary> exercises,
                                      List<PersonalRecordEntry> personalRecords) {
    public record ExerciseSummary(String exerciseName, List<SetEntry> sets) {
    }

    public record PersonalRecordEntry(String exerciseName, double weight, double previousBest) {
    }
}
