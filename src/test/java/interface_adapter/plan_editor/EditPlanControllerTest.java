package interface_adapter.plan_editor;

import org.junit.jupiter.api.Test;
import use_case.edit_plan.EditPlanInputBoundary;
import use_case.edit_plan.EditPlanInputData;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EditPlanControllerTest {
    private final RecordingInteractor interactor = new RecordingInteractor();
    private final EditPlanController controller = new EditPlanController(interactor);
    private final UUID planId = UUID.randomUUID();

    @Test
    void rowsArriveInOrderWithTrimmedNameTest() {
        controller.execute(planId, "  Full Body ", List.of("0043", "0025"), List.of(5, 4), List.of(5, 10),
                Arrays.asList(100.0, null));

        assertEquals(new EditPlanInputData(planId, "Full Body", List.of(
                new EditPlanInputData.ExerciseEntry("0043", 5, 5, 100.0),
                new EditPlanInputData.ExerciseEntry("0025", 4, 10, null))), interactor.inputData);
    }

    @Test
    void missingNameAndExercisesTest() {
        controller.execute(planId, null, null, null, null, null);

        assertNull(interactor.inputData.name());
        assertEquals(List.of(), interactor.inputData.exercises());
    }

    /** Remembers what the controller sent. */
    private static class RecordingInteractor implements EditPlanInputBoundary {
        private EditPlanInputData inputData;

        @Override
        public void execute(EditPlanInputData inputData) {
            this.inputData = inputData;
        }
    }
}
