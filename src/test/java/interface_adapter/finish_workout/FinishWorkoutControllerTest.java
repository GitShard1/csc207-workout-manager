package interface_adapter.finish_workout;

import org.junit.jupiter.api.Test;
import use_case.finish_workout.FinishWorkoutInputBoundary;
import use_case.finish_workout.FinishWorkoutInputData;
import use_case.workout_common.SetEntry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinishWorkoutControllerTest {
    private final RecordingInteractor interactor = new RecordingInteractor();
    private final FinishWorkoutController controller = new FinishWorkoutController(interactor);
    private final UUID planId = UUID.randomUUID();

    @Test
    void entriesAndSetsArriveInOrderTest() {
        controller.execute(planId, List.of("0025", "0043"),
                List.of(List.of(60.0, 62.5), List.of(100.0)),
                List.of(List.of(8, 6), List.of(5)));

        assertEquals(new FinishWorkoutInputData(planId, List.of(
                new FinishWorkoutInputData.ExerciseEntry("0025", List.of(
                        new SetEntry(60.0, 8),
                        new SetEntry(62.5, 6))),
                new FinishWorkoutInputData.ExerciseEntry("0043", List.of(
                        new SetEntry(100.0, 5))))), interactor.inputData);
    }

    @Test
    void missingExercisesTest() {
        controller.execute(planId, null, null, null);

        assertEquals(planId, interactor.inputData.planId());
        assertEquals(List.of(), interactor.inputData.exercises());
    }

    @Test
    void exerciseWithNoSetsTest() {
        controller.execute(planId, List.of("0025"), List.of(List.of()), List.of(List.of()));

        assertEquals(List.of(new FinishWorkoutInputData.ExerciseEntry("0025", List.of())),
                interactor.inputData.exercises());
    }

    /** Remembers what the controller sent. */
    private static class RecordingInteractor implements FinishWorkoutInputBoundary {
        private FinishWorkoutInputData inputData;

        @Override
        public void execute(FinishWorkoutInputData inputData) {
            this.inputData = inputData;
        }
    }
}
