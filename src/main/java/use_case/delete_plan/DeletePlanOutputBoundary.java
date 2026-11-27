package use_case.delete_plan;

public interface DeletePlanOutputBoundary {
    void prepareSuccessView(DeletePlanOutputData outputData);
    void prepareFailView(String errorMessage);
}
