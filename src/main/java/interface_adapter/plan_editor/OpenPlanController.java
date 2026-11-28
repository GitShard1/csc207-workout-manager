package interface_adapter.plan_editor;

import use_case.open_plan.OpenPlanInputBoundary;
import use_case.open_plan.OpenPlanInputData;

import java.util.UUID;

public class OpenPlanController {
    private final OpenPlanInputBoundary interactor;

    public OpenPlanController(OpenPlanInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void execute(UUID planId) {
        interactor.execute(new OpenPlanInputData(planId));
    }
}
