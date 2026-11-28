package use_case.finish_workout;

import use_case.workout_common.SetEntry;

import java.util.List;
import java.util.UUID;

/** A null list of exercises or sets is treated as empty. */
public record FinishWorkoutInputData(UUID planId, List<ExerciseEntry> exercises) {
    public FinishWorkoutInputData {
        exercises = copyOrEmpty(exercises);
    }

    private static <T> List<T> copyOrEmpty(List<T> list) {
        if (list == null) {
            return List.of();
        }
        return List.copyOf(list);
    }

    /** One exercise row from the workout screen, with only the sets that were ticked as done. */
    public record ExerciseEntry(String exerciseId, List<SetEntry> completedSets) {
        public ExerciseEntry {
            completedSets = copyOrEmpty(completedSets);
        }
    }
}
