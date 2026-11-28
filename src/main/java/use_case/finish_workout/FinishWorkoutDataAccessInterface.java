package use_case.finish_workout;

import entity.Workout;
import use_case.workout_common.WorkoutStorageUnavailableException;

import java.util.List;
import java.util.UUID;

public interface FinishWorkoutDataAccessInterface {
    /**
     * Returns the owner's saved workouts, newest first.
     * Throws WorkoutStorageUnavailableException if they can't be reached.
     */
    List<Workout> getWorkouts(UUID ownerId) throws WorkoutStorageUnavailableException;

    /** Saves a new workout. Throws WorkoutStorageUnavailableException if it could not be saved. */
    void save(Workout workout) throws WorkoutStorageUnavailableException;
}
