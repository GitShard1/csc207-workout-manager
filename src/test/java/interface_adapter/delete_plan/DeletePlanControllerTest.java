package interface_adapter.delete_plan;

import org.junit.jupiter.api.Test;
import use_case.delete_plan.DeletePlanInputBoundary;
import use_case.delete_plan.DeletePlanInputData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeletePlanControllerTest {
    private final RecordingInteractor interactor = new RecordingInteractor();
    private final DeletePlanController controller = new DeletePlanController(interactor);

    @Test
    void passesThePlanIdTest() {
        UUID planId = UUID.randomUUID();

        controller.execute(planId);

        assertEquals(new DeletePlanInputData(planId), interactor.inputData);
    }

    /** Remembers what the controller sent. */
    private static class RecordingInteractor implements DeletePlanInputBoundary {
        private DeletePlanInputData inputData;

        @Override
        public void execute(DeletePlanInputData inputData) {
            this.inputData = inputData;
        }
    }
}
