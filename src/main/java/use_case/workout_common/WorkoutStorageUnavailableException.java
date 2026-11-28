package use_case.workout_common;

/**
 * Thrown when workouts can't be saved or loaded, for example when there is no internet. The use case owns this
 * type, so it can react to the failure without knowing which storage caused it.
 */
public class WorkoutStorageUnavailableException extends Exception {

    /**
     * Creates the exception.
     *
     * @param message what went wrong, for logs and debugging
     * @param cause   the underlying error, or {@code null} if there is none
     */
    public WorkoutStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
