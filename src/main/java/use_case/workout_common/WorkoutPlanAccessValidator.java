package use_case.workout_common;

import entity.User;
import entity.WorkoutPlan;

public final class WorkoutPlanAccessValidator {
    private WorkoutPlanAccessValidator() {
    }

    /**
     Checks that the plan exists and the user is allowed to work out from it. Starter plans are allowed.
     Returns the problem, or empty string when allowed.
     */
    public static String checkStartable(User currentUser, WorkoutPlan plan) {
        if (plan == null) {
            return "Plan not found.";
        }
        else if (!plan.isPreset() && !currentUser.getUserID().equals(plan.getUserOwnerID())) {
            return "You can only log workouts from your own plans.";
        }
        else {
            return "";
        }
    }
}
