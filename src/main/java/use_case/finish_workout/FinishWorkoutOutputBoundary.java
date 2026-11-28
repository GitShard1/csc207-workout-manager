package use_case.finish_workout;

public interface FinishWorkoutOutputBoundary {
    void prepareSuccessView(FinishWorkoutOutputData outputData);

    void prepareFailView(String errorMessage);

    /** No sets were completed, so the user is asked whether to discard the workout. Nothing was saved. */
    void prepareNoSetsView();
}
