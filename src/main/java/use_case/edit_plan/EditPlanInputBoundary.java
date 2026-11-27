package use_case.edit_plan;

import java.util.UUID;

public interface EditPlanInputBoundary {
    /** Shows the plan's name, exercises and targets so the user can change them. */
    void loadPlan(UUID planId);

    void execute(EditPlanInputData inputData);
}
