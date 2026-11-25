package use_case.create_plan;

import entity.WorkoutPlan;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.UUID;

public interface CreatePlanDataAccessInterface {
    /** Returns true if this owner already has a plan with this name, compared ignoring case. */
    boolean existsByName(UUID ownerId, String planName);

    /** Saves a new plan. Throws PlanStorageUnavailableException if it could not be saved. */
    void save(WorkoutPlan workoutPlan) throws PlanStorageUnavailableException;
}
