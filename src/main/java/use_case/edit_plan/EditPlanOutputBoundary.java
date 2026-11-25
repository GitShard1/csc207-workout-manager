package use_case.edit_plan;

public interface EditPlanOutputBoundary {
    void prepareSuccessView(EditPlanOutputData outputData);
    void prepareDeleteSuccessView();
    void prepareFailView(String errorMessage);
}
