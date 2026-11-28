package interface_adapter.finish_workout;

import interface_adapter.ViewManagerModel;
import interface_adapter.log_workout.LogWorkoutState;
import interface_adapter.log_workout.LogWorkoutViewModel;
import interface_adapter.workout_summary.WorkoutSummaryState;
import interface_adapter.workout_summary.WorkoutSummaryViewModel;
import org.junit.jupiter.api.Test;
import use_case.finish_workout.FinishWorkoutOutputData;
import use_case.workout_common.SetEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinishWorkoutPresenterTest {
    private final LogWorkoutViewModel logWorkoutViewModel = new LogWorkoutViewModel();
    private final WorkoutSummaryViewModel summaryViewModel = new WorkoutSummaryViewModel();
    private final ViewManagerModel viewManagerModel = new ViewManagerModel(LogWorkoutViewModel.VIEW_NAME);
    private final FinishWorkoutPresenter presenter = new FinishWorkoutPresenter(logWorkoutViewModel,
            summaryViewModel, viewManagerModel);

    private final List<LogWorkoutState.ExerciseRow> exercises = List.of(
            new LogWorkoutState.ExerciseRow("0025", "Barbell Bench Press", "Chest - target 3 x 8 at 60 kg",
                    List.of(new LogWorkoutState.SetRow("", 60.0, 8))));

    private void startWorkout() {
        logWorkoutViewModel.setState(new LogWorkoutState(UUID.randomUUID(), "Push Day", exercises, null, false));
    }

    private FinishWorkoutOutputData finishedWorkout(List<FinishWorkoutOutputData.PersonalRecordEntry> records) {
        return new FinishWorkoutOutputData("Push Day", LocalDate.of(2026, 11, 27), List.of(
                new FinishWorkoutOutputData.ExerciseSummary("Barbell Bench Press", List.of(
                        new SetEntry(60.0, 8),
                        new SetEntry(62.5, 6))),
                new FinishWorkoutOutputData.ExerciseSummary("Barbell Squat", List.of(
                        new SetEntry(100.0, 5)))), records);
    }

    @Test
    void successTest() {
        startWorkout();

        presenter.prepareSuccessView(finishedWorkout(List.of(
                new FinishWorkoutOutputData.PersonalRecordEntry("Barbell Bench Press", 62.5, 60.0))));

        WorkoutSummaryState summary = summaryViewModel.getState();
        assertEquals("Push Day", summary.planName());
        assertEquals("Friday, November 27, 2026", summary.date());
        assertEquals(3, summary.totalSets());
        assertEquals(List.of(
                new WorkoutSummaryState.ExerciseLine("Barbell Bench Press", "60 kg x 8, 62.5 kg x 6"),
                new WorkoutSummaryState.ExerciseLine("Barbell Squat", "100 kg x 5")), summary.exercises());
        assertEquals(List.of("Barbell Bench Press: 62.5 kg (previous best 60 kg)"), summary.personalRecords());
        assertEquals(LogWorkoutState.empty(), logWorkoutViewModel.getState());
        assertEquals(WorkoutSummaryViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void successNoRecordsTest() {
        presenter.prepareSuccessView(finishedWorkout(List.of()));

        assertEquals(List.of(), summaryViewModel.getState().personalRecords());
    }

    @Test
    void failTest() {
        startWorkout();
        viewManagerModel.setState(LogWorkoutViewModel.VIEW_NAME);

        presenter.prepareFailView("Exercise 1, set 1: Weight can't be negative.");

        LogWorkoutState state = logWorkoutViewModel.getState();
        assertEquals("Exercise 1, set 1: Weight can't be negative.", state.error());
        assertEquals(exercises, state.exercises());
        assertFalse(state.askDiscard());
        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void noSetsTest() {
        startWorkout();
        viewManagerModel.setState(LogWorkoutViewModel.VIEW_NAME);

        presenter.prepareNoSetsView();

        LogWorkoutState state = logWorkoutViewModel.getState();
        assertTrue(state.askDiscard());
        assertEquals(exercises, state.exercises());
        assertNull(state.error());
        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
    }
}
