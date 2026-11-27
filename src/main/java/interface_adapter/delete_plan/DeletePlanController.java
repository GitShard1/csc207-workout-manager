package interface_adapter.delete_plan;

import use_case.delete_plan.DeletePlanInputBoundary;
import use_case.delete_plan.DeletePlanInputData;

import java.util.UUID;

public class DeletePlanController {
    private final DeletePlanInputBoundary interactor;

    public DeletePlanController(DeletePlanInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void execute(UUID planId) {
        interactor.execute(new DeletePlanInputData(planId));
    }
}
