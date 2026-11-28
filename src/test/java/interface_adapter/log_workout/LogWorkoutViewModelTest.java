package interface_adapter.log_workout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogWorkoutViewModelTest {
    private final LogWorkoutViewModel viewModel = new LogWorkoutViewModel();

    @Test
    void viewNameTest() {
        assertEquals("Log Workout", LogWorkoutViewModel.VIEW_NAME);
        assertEquals("Log Workout", viewModel.getViewName());
        assertEquals(LogWorkoutState.empty(), viewModel.getState());
    }

    @Test
    void showErrorKeepsTheExercisesAndNotifiesTest() {
        List<LogWorkoutState.ExerciseRow> exercises = List.of(
                new LogWorkoutState.ExerciseRow("0025", "Barbell Bench Press", "3 x 8", "",
                        List.of(new LogWorkoutState.SetRow("", "8"))));
        viewModel.setState(new LogWorkoutState(UUID.randomUUID(), "Push Day", exercises, null, false));
        int[] notifications = {0};
        viewModel.addPropertyChangeListener(event -> notifications[0]++);

        viewModel.showError("Plan not found.");

        assertEquals("Plan not found.", viewModel.getState().error());
        assertEquals(exercises, viewModel.getState().exercises());
        assertEquals("Push Day", viewModel.getState().planName());
        assertEquals(1, notifications[0]);
    }
}
