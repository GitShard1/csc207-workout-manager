package interface_adapter.start_workout;

import interface_adapter.ViewManagerModel;
import interface_adapter.log_workout.LogWorkoutState;
import interface_adapter.log_workout.LogWorkoutViewModel;
import interface_adapter.log_workout.WorkoutFormat;
import use_case.start_workout.StartWorkoutOutputBoundary;
import use_case.start_workout.StartWorkoutOutputData;
import use_case.workout_common.SetEntry;

import java.util.ArrayList;
import java.util.List;

public class StartWorkoutPresenter implements StartWorkoutOutputBoundary {
    private final LogWorkoutViewModel viewModel;
    private final ViewManagerModel viewManagerModel;

    public StartWorkoutPresenter(LogWorkoutViewModel viewModel, ViewManagerModel viewManagerModel) {
        this.viewModel = viewModel;
        this.viewManagerModel = viewManagerModel;
    }

    @Override
    public void prepareSuccessView(StartWorkoutOutputData outputData) {
        final ArrayList<LogWorkoutState.ExerciseRow> exerciseRows = new ArrayList<>();
        for (StartWorkoutOutputData.ExerciseEntry entry : outputData.exercises()) {
            exerciseRows.add(new LogWorkoutState.ExerciseRow(entry.exerciseId(), entry.exerciseName(),
                    WorkoutFormat.target(entry.targetSets(), entry.targetReps(), entry.targetWeight()),
                    WorkoutFormat.sets(entry.lastSets()), buildSetRows(entry)));
        }

        viewModel.setState(new LogWorkoutState(outputData.planId(), outputData.planName(), exerciseRows, null,
                false));
        viewManagerModel.setState(LogWorkoutViewModel.VIEW_NAME);
    }

    @Override
    public void prepareFailView(String errorMessage) {
        viewModel.showError(errorMessage);
    }

    private List<LogWorkoutState.SetRow> buildSetRows(StartWorkoutOutputData.ExerciseEntry entry) {
        final ArrayList<LogWorkoutState.SetRow> setRows = new ArrayList<>();
        for (int i = 0; i < entry.targetSets(); i++) {
            if (i < entry.lastSets().size()) {
                final SetEntry lastSet = entry.lastSets().get(i);
                setRows.add(new LogWorkoutState.SetRow(WorkoutFormat.weight(lastSet.weight()),
                        String.valueOf(lastSet.reps())));
            }
            else {
                setRows.add(new LogWorkoutState.SetRow(targetWeightText(entry.targetWeight()),
                        String.valueOf(entry.targetReps())));
            }
        }
        return setRows;
    }

    private String targetWeightText(Double targetWeight) {
        if (targetWeight == null) {
            return "";
        }
        return WorkoutFormat.weight(targetWeight);
    }
}
