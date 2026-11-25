package interface_adapter.edit_plan;

import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import use_case.edit_plan.EditPlanOutputBoundary;
import use_case.edit_plan.EditPlanOutputData;

import java.util.ArrayList;

public class EditPlanPresenter implements EditPlanOutputBoundary {
    private final PlanEditorViewModel viewModel;

    public EditPlanPresenter(PlanEditorViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareSuccessView(EditPlanOutputData outputData) {
        ArrayList<PlanEditorState.ExerciseRow> exerciseRows = new ArrayList<>();
        for (EditPlanOutputData.ExerciseEntry entry : outputData.exercises()) {
            exerciseRows.add(new PlanEditorState.ExerciseRow(entry.exerciseId(), entry.exerciseName(), entry.sets(),
                    entry.reps(), entry.weight()));
        }

        PlanEditorState state = new PlanEditorState();
        state.setPlanId(outputData.planId());
        state.setName(outputData.name());
        state.setExercises(exerciseRows);
        viewModel.setState(state);
        viewModel.firePropertyChanged();
    }

    @Override
    public void prepareDeleteSuccessView() {
        viewModel.setState(new PlanEditorState());
        viewModel.firePropertyChanged();
    }

    @Override
    public void prepareFailView(String errorMessage) {
        viewModel.getState().setError(errorMessage);
        viewModel.firePropertyChanged();
    }
}
