package interface_adapter.log_workout;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import org.junit.jupiter.api.Test;
import use_case.start_workout.StartWorkoutOutputData;
import use_case.workout_common.SetEntry;

import java.time.LocalDate;
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

    private StartWorkoutOutputData.ExerciseEntry benchPress(List<SetEntry> lastSets, LocalDate lastDate) {
        return new StartWorkoutOutputData.ExerciseEntry("0025", "Barbell Bench Press", "CHEST", "Barbell", 3, 8,
                60.0, lastSets, lastDate);
    }

    private StartWorkoutOutputData workout(StartWorkoutOutputData.ExerciseEntry... entries) {
        return new StartWorkoutOutputData(planId, "Push Day", List.of(entries));
    }

    @Test
    void successTest() {
        presenter.prepareSuccessView(workout(
                benchPress(List.of(new SetEntry(60.0, 8), new SetEntry(62.5, 6)), LocalDate.of(2026, 11, 20)),
                new StartWorkoutOutputData.ExerciseEntry("0043", "Barbell Squat", "UPPER_LEGS", "Barbell", 5, 5,
                        null, List.of(), null)));

        LogWorkoutState state = logWorkoutViewModel.getState();
        assertEquals(planId, state.planId());
        assertEquals("Push Day", state.planName());
        assertNull(state.error());
        assertFalse(state.askDiscard());
        assertEquals(2, state.exercises().size());

        LogWorkoutState.ExerciseRow bench = state.exercises().get(0);
        assertEquals("0025", bench.exerciseId());
        assertEquals("Barbell Bench Press", bench.exerciseName());
        assertEquals("Chest - Barbell - target 3 x 8 at 60 kg - last time Nov 20", bench.details());
        assertEquals(3, bench.sets().size());

        LogWorkoutState.ExerciseRow squat = state.exercises().get(1);
        assertEquals("Upper legs - Barbell - target 5 x 5", squat.details());
        assertEquals(5, squat.sets().size());

        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void detailsSkipNullEquipmentTest() {
        presenter.prepareSuccessView(workout(new StartWorkoutOutputData.ExerciseEntry("0001", "Push-up", "CHEST",
                null, 3, 10, null, List.of(), null)));

        assertEquals("Chest - target 3 x 10", logWorkoutViewModel.getState().exercises().get(0).details());
    }

    @Test
    void successPrefillTest() {
        presenter.prepareSuccessView(workout(benchPress(
                List.of(new SetEntry(62.5, 6), new SetEntry(65.0, 5)), LocalDate.of(2026, 11, 20))));

        assertEquals(List.of(new LogWorkoutState.SetRow("62.5 kg x 6", 62.5, 6),
                new LogWorkoutState.SetRow("65 kg x 5", 65.0, 5),
                new LogWorkoutState.SetRow("", 60.0, 8)), logWorkoutViewModel.getState().exercises().get(0).sets());
    }

    @Test
    void successNeverDoneTest() {
        presenter.prepareSuccessView(workout(benchPress(List.of(), null)));

        LogWorkoutState.ExerciseRow row = logWorkoutViewModel.getState().exercises().get(0);
        assertEquals("Chest - Barbell - target 3 x 8 at 60 kg", row.details());
        assertEquals(List.of(new LogWorkoutState.SetRow("", 60.0, 8), new LogWorkoutState.SetRow("", 60.0, 8),
                new LogWorkoutState.SetRow("", 60.0, 8)), row.sets());
    }

    @Test
    void successNoTargetWeightTest() {
        presenter.prepareSuccessView(workout(new StartWorkoutOutputData.ExerciseEntry("0043", "Barbell Squat",
                "UPPER_LEGS", "Barbell", 2, 5, null, List.of(), null)));

        assertEquals(List.of(new LogWorkoutState.SetRow("", null, 5), new LogWorkoutState.SetRow("", null, 5)),
                logWorkoutViewModel.getState().exercises().get(0).sets());
    }

    @Test
    void failTest() {
        presenter.prepareSuccessView(workout(benchPress(List.of(), null)));
        viewManagerModel.setState(HomeViewModel.VIEW_NAME);

        presenter.prepareFailView("Plan not found.");

        assertEquals("Plan not found.", logWorkoutViewModel.getState().error());
        assertEquals(1, logWorkoutViewModel.getState().exercises().size());
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
    }
}
