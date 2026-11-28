package interface_adapter.plan_editor;

import org.junit.jupiter.api.Test;
import use_case.create_plan.CreatePlanInputBoundary;
import use_case.create_plan.CreatePlanInputData;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreatePlanControllerTest {
    private final RecordingInteractor interactor = new RecordingInteractor();
    private final CreatePlanController controller = new CreatePlanController(interactor);

    @Test
    void rowsArriveInOrderWithTrimmedNameTest() {
        controller.execute("  Leg Day ", List.of("0043", "0025"), List.of(5, 4), List.of(5, 10),
                Arrays.asList(100.0, null));

        assertEquals(new CreatePlanInputData("Leg Day", List.of(
                new CreatePlanInputData.ExerciseEntry("0043", 5, 5, 100.0),
                new CreatePlanInputData.ExerciseEntry("0025", 4, 10, null))), interactor.inputData);
    }

    @Test
    void missingNameAndExercisesTest() {
        controller.execute(null, null, null, null, null);

        assertNull(interactor.inputData.name());
        assertEquals(List.of(), interactor.inputData.exercises());
    }

    /** Remembers what the controller sent. */
    private static class RecordingInteractor implements CreatePlanInputBoundary {
        private CreatePlanInputData inputData;

        @Override
        public void execute(CreatePlanInputData inputData) {
            this.inputData = inputData;
        }
    }
}
