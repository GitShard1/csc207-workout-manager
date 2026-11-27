package interface_adapter.open_plan;

import interface_adapter.ViewManagerModel;
import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import use_case.open_plan.OpenPlanOutputBoundary;
import use_case.open_plan.OpenPlanOutputData;

import java.util.ArrayList;

public class OpenPlanPresenter implements OpenPlanOutputBoundary {
    private final PlanEditorViewModel viewModel;
    private final ViewManagerModel viewManagerModel;

    public OpenPlanPresenter(PlanEditorViewModel viewModel, ViewManagerModel viewManagerModel) {
        this.viewModel = viewModel;
        this.viewManagerModel = viewManagerModel;
    }

    @Override
    public void prepareSuccessView(OpenPlanOutputData outputData) {
        final ArrayList<PlanEditorState.ExerciseRow> exerciseRows = new ArrayList<>();
        for (OpenPlanOutputData.ExerciseEntry entry : outputData.exercises()) {
            exerciseRows.add(new PlanEditorState.ExerciseRow(entry.exerciseId(), entry.exerciseName(), entry.sets(),
                    entry.reps(), entry.weight()));
        }

        viewModel.setState(new PlanEditorState(outputData.planId(), outputData.name(), exerciseRows, null));
        viewManagerModel.setState(PlanEditorViewModel.VIEW_NAME);
    }

    @Override
    public void prepareFailView(String errorMessage) {
        viewModel.showError(errorMessage);
    }
}
