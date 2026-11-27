package interface_adapter.delete_plan;

import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import use_case.delete_plan.DeletePlanOutputBoundary;
import use_case.delete_plan.DeletePlanOutputData;

public class DeletePlanPresenter implements DeletePlanOutputBoundary {
    private final PlanEditorViewModel viewModel;

    public DeletePlanPresenter(PlanEditorViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareSuccessView(DeletePlanOutputData outputData) {
        viewModel.setState(PlanEditorState.empty());
    }

    @Override
    public void prepareFailView(String errorMessage) {
        viewModel.showError(errorMessage);
    }
}
