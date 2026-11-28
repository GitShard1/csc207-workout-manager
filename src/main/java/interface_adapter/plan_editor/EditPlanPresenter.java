package interface_adapter.plan_editor;

import interface_adapter.ViewManagerModel;
import use_case.edit_plan.EditPlanOutputBoundary;
import use_case.edit_plan.EditPlanOutputData;

import java.util.ArrayList;

public class EditPlanPresenter implements EditPlanOutputBoundary {
    private final PlanEditorViewModel viewModel;
    private final ViewManagerModel viewManagerModel;

    public EditPlanPresenter(PlanEditorViewModel viewModel, ViewManagerModel viewManagerModel) {
        this.viewModel = viewModel;
        this.viewManagerModel = viewManagerModel;
    }

    @Override
    public void prepareSuccessView(EditPlanOutputData outputData) {
        final ArrayList<PlanEditorState.ExerciseRow> exerciseRows = new ArrayList<>();
        for (EditPlanOutputData.ExerciseEntry entry : outputData.exercises()) {
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
