package interface_adapter.plan_editor;

import org.junit.jupiter.api.Test;
import use_case.open_plan.OpenPlanInputBoundary;
import use_case.open_plan.OpenPlanInputData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenPlanControllerTest {
    private final RecordingInteractor interactor = new RecordingInteractor();
    private final OpenPlanController controller = new OpenPlanController(interactor);

    @Test
    void passesThePlanIdTest() {
        UUID planId = UUID.randomUUID();

        controller.execute(planId);

        assertEquals(new OpenPlanInputData(planId), interactor.inputData);
    }

    /** Remembers what the controller sent. */
    private static class RecordingInteractor implements OpenPlanInputBoundary {
        private OpenPlanInputData inputData;

        @Override
        public void execute(OpenPlanInputData inputData) {
            this.inputData = inputData;
        }
    }
}
