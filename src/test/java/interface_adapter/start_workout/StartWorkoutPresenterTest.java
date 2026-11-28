package interface_adapter.start_workout;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.log_workout.LogWorkoutState;
import interface_adapter.log_workout.LogWorkoutViewModel;
import org.junit.jupiter.api.Test;
import use_case.start_workout.StartWorkoutOutputData;
import use_case.workout_common.SetEntry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class StartWorkoutPresenterTest {
    private final LogWorkoutViewModel logWorkoutViewModel = new LogWorkoutViewModel();
    private final ViewManagerModel viewManagerModel = new ViewManagerModel(HomeViewModel.VIEW_NAME);
    private final StartWorkoutPresenter presenter = new StartWorkoutPresenter(logWorkoutViewModel,
            viewManagerModel);

    private final UUID planId = UUID.randomUUID();

    private StartWorkoutOutputData.ExerciseEntry benchPress(List<SetEntry> lastSets) {
        return new StartWorkoutOutputData.ExerciseEntry("0025", "Barbell Bench Press", 3, 8, 60.0, lastSets);
    }

    @Test
    void successTest() {
        presenter.prepareSuccessView(new StartWorkoutOutputData(planId, "Push Day", List.of(
                benchPress(List.of(new SetEntry(60.0, 8),
                        new SetEntry(62.5, 6))),
                new StartWorkoutOutputData.ExerciseEntry("0043", "Barbell Squat", 5, 5, null, List.of()))));

        LogWorkoutState state = logWorkoutViewModel.getState();
        assertEquals(planId, state.planId());
        assertEquals("Push Day", state.planName());
        assertNull(state.error());
        assertFalse(state.askDiscard());
        assertEquals(2, state.exercises().size());

        LogWorkoutState.ExerciseRow bench = state.exercises().get(0);
        assertEquals("0025", bench.exerciseId());
        assertEquals("Barbell Bench Press", bench.exerciseName());
        assertEquals("3 x 8 at 60 kg", bench.target());
        assertEquals("60 kg x 8, 62.5 kg x 6", bench.lastTime());
        assertEquals(3, bench.sets().size());

        LogWorkoutState.ExerciseRow squat = state.exercises().get(1);
        assertEquals("5 x 5", squat.target());
        assertEquals("", squat.lastTime());
        assertEquals(5, squat.sets().size());

        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void successPrefillTest() {
        presenter.prepareSuccessView(new StartWorkoutOutputData(planId, "Push Day", List.of(
                benchPress(List.of(new SetEntry(62.5, 6),
                        new SetEntry(65.0, 5))))));

        assertEquals(List.of(new LogWorkoutState.SetRow("62.5", "6"), new LogWorkoutState.SetRow("65", "5"),
                new LogWorkoutState.SetRow("60", "8")), logWorkoutViewModel.getState().exercises().get(0).sets());
    }

    @Test
    void successNeverDoneTest() {
        presenter.prepareSuccessView(new StartWorkoutOutputData(planId, "Push Day",
                List.of(benchPress(List.of()))));

        LogWorkoutState.ExerciseRow row = logWorkoutViewModel.getState().exercises().get(0);
        assertEquals("", row.lastTime());
        assertEquals(List.of(new LogWorkoutState.SetRow("60", "8"), new LogWorkoutState.SetRow("60", "8"),
                new LogWorkoutState.SetRow("60", "8")), row.sets());
    }

    @Test
    void successNoTargetWeightTest() {
        presenter.prepareSuccessView(new StartWorkoutOutputData(planId, "Leg Day", List.of(
                new StartWorkoutOutputData.ExerciseEntry("0043", "Barbell Squat", 2, 5, null, List.of()))));

        assertEquals(List.of(new LogWorkoutState.SetRow("", "5"), new LogWorkoutState.SetRow("", "5")),
                logWorkoutViewModel.getState().exercises().get(0).sets());
    }

    @Test
    void failTest() {
        presenter.prepareSuccessView(new StartWorkoutOutputData(planId, "Push Day",
                List.of(benchPress(List.of()))));
        viewManagerModel.setState(HomeViewModel.VIEW_NAME);

        presenter.prepareFailView("Plan not found.");

        assertEquals("Plan not found.", logWorkoutViewModel.getState().error());
        assertEquals(1, logWorkoutViewModel.getState().exercises().size());
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
    }
}
