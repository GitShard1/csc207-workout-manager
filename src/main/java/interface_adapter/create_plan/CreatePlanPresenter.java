package interface_adapter.create_plan;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import use_case.create_plan.CreatePlanOutputBoundary;
import use_case.create_plan.CreatePlanOutputData;

public class CreatePlanPresenter implements CreatePlanOutputBoundary {
    private final PlanEditorViewModel viewModel;
    private final ViewManagerModel viewManagerModel;

    public CreatePlanPresenter(PlanEditorViewModel viewModel, ViewManagerModel viewManagerModel) {
        this.viewModel = viewModel;
        this.viewManagerModel = viewManagerModel;
    }

    @Override
    public void prepareSuccessView(CreatePlanOutputData outputData) {
        viewModel.setState(PlanEditorState.empty());
        viewManagerModel.setState(HomeViewModel.VIEW_NAME);
    }

    @Override
    public void prepareFailView(String errorMessage) {
        viewModel.showError(errorMessage);
    }
}
