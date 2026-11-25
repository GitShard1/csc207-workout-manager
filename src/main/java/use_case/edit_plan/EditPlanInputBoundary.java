package use_case.edit_plan;

import java.util.UUID;

public interface EditPlanInputBoundary {
    /** Shows the plan's name, exercises and targets so the user can change them. */
    void loadPlan(UUID planId);

    void execute(EditPlanInputData inputData);

    /** Removes the plan from the user's list. The caller is expected to have asked the user to confirm. */
    void deletePlan(UUID planId);
}
