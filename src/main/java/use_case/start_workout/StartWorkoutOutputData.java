package use_case.start_workout;

import use_case.workout_common.SetEntry;

import java.util.List;
import java.util.UUID;

/** {@code lastSets} is empty when the user has never done that exercise. */
public record StartWorkoutOutputData(UUID planId, String planName, List<ExerciseEntry> exercises) {
    public record ExerciseEntry(String exerciseId, String exerciseName, int targetSets, int targetReps,
                                Double targetWeight, List<SetEntry> lastSets) {
    }
}
