package interface_adapter.log_workout;

import interface_adapter.ViewManagerModel;
import use_case.start_workout.StartWorkoutOutputBoundary;
import use_case.start_workout.StartWorkoutOutputData;
import use_case.workout_common.SetEntry;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StartWorkoutPresenter implements StartWorkoutOutputBoundary {
    private static final DateTimeFormatter LAST_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

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
                    buildDetails(entry), buildSetRows(entry)));
        }

        viewModel.setState(new LogWorkoutState(outputData.planId(), outputData.planName(), exerciseRows, null,
                false));
        viewManagerModel.setState(LogWorkoutViewModel.VIEW_NAME);
    }

    @Override
    public void prepareFailView(String errorMessage) {
        viewModel.showError(errorMessage);
    }

    private String buildDetails(StartWorkoutOutputData.ExerciseEntry entry) {
        final ArrayList<String> parts = new ArrayList<>();
        parts.add(WorkoutFormat.bodyPart(entry.bodyPart()));
        parts.add(entry.equipment());
        parts.add("target " + WorkoutFormat.target(entry.targetSets(), entry.targetReps(), entry.targetWeight()));
        if (entry.lastDate() != null) {
            parts.add("last time " + LAST_DATE.format(entry.lastDate()));
        }

        final ArrayList<String> shownParts = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                shownParts.add(part);
            }
        }
        return String.join(" - ", shownParts);
    }

    private List<LogWorkoutState.SetRow> buildSetRows(StartWorkoutOutputData.ExerciseEntry entry) {
        final ArrayList<LogWorkoutState.SetRow> setRows = new ArrayList<>();
        for (int i = 0; i < entry.targetSets(); i++) {
            if (i < entry.lastSets().size()) {
                final SetEntry lastSet = entry.lastSets().get(i);
                setRows.add(new LogWorkoutState.SetRow(WorkoutFormat.set(lastSet.weight(), lastSet.reps()),
                        lastSet.weight(), lastSet.reps()));
            }
            else {
                setRows.add(new LogWorkoutState.SetRow("", entry.targetWeight(), entry.targetReps()));
            }
        }
        return setRows;
    }
}
