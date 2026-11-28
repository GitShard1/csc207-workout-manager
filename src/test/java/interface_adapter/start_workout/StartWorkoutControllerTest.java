package interface_adapter.start_workout;

import org.junit.jupiter.api.Test;
import use_case.start_workout.StartWorkoutInputBoundary;
import use_case.start_workout.StartWorkoutInputData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartWorkoutControllerTest {
    private final RecordingInteractor interactor = new RecordingInteractor();
    private final StartWorkoutController controller = new StartWorkoutController(interactor);

    @Test
    void passesThePlanIdTest() {
        UUID planId = UUID.randomUUID();

        controller.execute(planId);

        assertEquals(new StartWorkoutInputData(planId), interactor.inputData);
    }

    /** Remembers what the controller sent. */
    private static class RecordingInteractor implements StartWorkoutInputBoundary {
        private StartWorkoutInputData inputData;

        @Override
        public void execute(StartWorkoutInputData inputData) {
            this.inputData = inputData;
        }
    }
}
