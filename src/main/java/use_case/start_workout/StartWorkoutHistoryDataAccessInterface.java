package use_case.start_workout;

import entity.Workout;
import use_case.workout_common.WorkoutStorageUnavailableException;

import java.util.List;
import java.util.UUID;

public interface StartWorkoutHistoryDataAccessInterface {
    /**
     * Returns the owner's saved workouts, newest first.
     * Throws WorkoutStorageUnavailableException if they can't be reached.
     */
    List<Workout> getWorkouts(UUID ownerId) throws WorkoutStorageUnavailableException;
}
