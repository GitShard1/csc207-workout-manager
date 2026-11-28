package interface_adapter.log_workout;

import interface_adapter.ViewManagerModel;
import interface_adapter.workout_summary.WorkoutSummaryState;
import interface_adapter.workout_summary.WorkoutSummaryViewModel;
import use_case.finish_workout.FinishWorkoutOutputBoundary;
import use_case.finish_workout.FinishWorkoutOutputData;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

public class FinishWorkoutPresenter implements FinishWorkoutOutputBoundary {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private final LogWorkoutViewModel logWorkoutViewModel;
    private final WorkoutSummaryViewModel summaryViewModel;
    private final ViewManagerModel viewManagerModel;

    public FinishWorkoutPresenter(LogWorkoutViewModel logWorkoutViewModel,
                                  WorkoutSummaryViewModel summaryViewModel, ViewManagerModel viewManagerModel) {
        this.logWorkoutViewModel = logWorkoutViewModel;
        this.summaryViewModel = summaryViewModel;
        this.viewManagerModel = viewManagerModel;
    }

    @Override
    public void prepareSuccessView(FinishWorkoutOutputData outputData) {
        int totalSets = 0;
        final ArrayList<WorkoutSummaryState.ExerciseLine> exerciseLines = new ArrayList<>();
        for (FinishWorkoutOutputData.ExerciseSummary summary : outputData.exercises()) {
            totalSets += summary.sets().size();
            exerciseLines.add(new WorkoutSummaryState.ExerciseLine(summary.exerciseName(),
                    WorkoutFormat.sets(summary.sets())));
        }

        final ArrayList<String> recordLines = new ArrayList<>();
        for (FinishWorkoutOutputData.PersonalRecordEntry personalRecord : outputData.personalRecords()) {
            recordLines.add(personalRecord.exerciseName() + ": " + WorkoutFormat.weight(personalRecord.weight())
                    + " kg (previous best " + WorkoutFormat.weight(personalRecord.previousBest()) + " kg)");
        }

        summaryViewModel.setState(new WorkoutSummaryState(outputData.planName(), DATE.format(outputData.date()),
                totalSets, exerciseLines, recordLines));
        logWorkoutViewModel.setState(LogWorkoutState.empty());
        viewManagerModel.setState(WorkoutSummaryViewModel.VIEW_NAME);
    }

    @Override
    public void prepareFailView(String errorMessage) {
        logWorkoutViewModel.showError(errorMessage);
    }

    @Override
    public void prepareNoSetsView() {
        logWorkoutViewModel.setState(logWorkoutViewModel.getState().withDiscardPrompt());
    }
}
