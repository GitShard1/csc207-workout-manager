package use_case.edit_plan;

import entity.WorkoutPlan;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.UUID;

public interface EditPlanDataAccessInterface {
    /** Returns null if no matching plan. Throws PlanStorageUnavailableException if plans can't be reached. */
    WorkoutPlan getById(UUID planId) throws PlanStorageUnavailableException;

    /**
     * Returns whether the owner has a plan with the given name, compared ignoring case.
     * Throws PlanStorageUnavailableException if plans can't be reached.
     */
    boolean existsByName(UUID ownerId, String planName) throws PlanStorageUnavailableException;

    /** Replaces the stored plan with the same ID. Throws PlanStorageUnavailableException if it could not be saved. */
    void update(WorkoutPlan workoutPlan) throws PlanStorageUnavailableException;
}
