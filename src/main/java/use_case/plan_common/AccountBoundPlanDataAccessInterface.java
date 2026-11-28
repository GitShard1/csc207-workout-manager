package use_case.plan_common;

import entity.WorkoutPlan;

import java.util.UUID;

/** Plan editor storage operations tied to the account that initiated the request. */
public interface AccountBoundPlanDataAccessInterface {
    WorkoutPlan getByIdFor(UUID viewer, UUID planId) throws PlanStorageUnavailableException;

    boolean existsByName(UUID ownerId, String planName) throws PlanStorageUnavailableException;

    void update(WorkoutPlan workoutPlan) throws PlanStorageUnavailableException;
}
