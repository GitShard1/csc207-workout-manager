package use_case.finish_workout;

import entity.WorkoutPlan;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.UUID;

public interface FinishWorkoutPlanDataAccessInterface {
    /** Returns null if no matching plan. Throws PlanStorageUnavailableException if plans can't be reached. */
    WorkoutPlan getById(UUID planId) throws PlanStorageUnavailableException;
}
