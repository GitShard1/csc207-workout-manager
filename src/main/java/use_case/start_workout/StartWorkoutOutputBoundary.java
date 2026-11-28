package use_case.start_workout;

public interface StartWorkoutOutputBoundary {
    void prepareSuccessView(StartWorkoutOutputData outputData);

    void prepareFailView(String errorMessage);
}
