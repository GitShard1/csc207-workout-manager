package use_case.delete_plan;

import entity.WorkoutPlan;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.UUID;

public interface DeletePlanDataAccessInterface {
    /** Returns null if no matching plan. Throws PlanStorageUnavailableException if plans can't be reached. */
    WorkoutPlan getById(UUID planId) throws PlanStorageUnavailableException;

    /**
     * Removes the plan with this ID. Workouts already logged from it are not changed.
     * Throws PlanStorageUnavailableException if it could not be removed.
     */
    void delete(UUID planId) throws PlanStorageUnavailableException;
}
