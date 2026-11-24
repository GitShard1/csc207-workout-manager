package use_case.edit_plan;

import entity.WorkoutPlan;

import java.util.UUID;

public interface EditPlanDataAccessInterface {
    /** Returns null if no matching plan. */
    WorkoutPlan getById(UUID planId);

    /** Returns whether the owner has a plan with the given name, compared ignoring case. */
    boolean existsByName(UUID ownerId, String planName);

    /** Replaces the stored plan with the same ID. */
    void update(WorkoutPlan workoutPlan);
}
