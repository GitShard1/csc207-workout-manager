package use_case.open_plan;

public interface OpenPlanOutputBoundary {
    void prepareSuccessView(OpenPlanOutputData outputData);
    void prepareFailView(String errorMessage);
}
