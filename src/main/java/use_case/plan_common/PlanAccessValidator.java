package use_case.plan_common;

import entity.User;
import entity.WorkoutPlan;

public final class PlanAccessValidator {
    private PlanAccessValidator() {
    }

    /**
     Checks that the plan exists and the user is allowed to change it.
     Returns the problem, or empty string when allowed.
     */
    public static String checkEditable(User currentUser, WorkoutPlan plan) {
        if (plan == null) {
            return "Plan not found.";
        }
        else if (plan.isPreset()) {
            return "Starter plans can't be edited.";
        }
        else if (!currentUser.getUserID().equals(plan.getUserOwnerID())) {
            return "You can only edit your own plans.";
        }
        return "";
    }
}
