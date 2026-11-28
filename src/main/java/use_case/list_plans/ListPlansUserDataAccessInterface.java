package use_case.list_plans;

import entity.WorkoutPlan;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.List;
import java.util.UUID;

/**
 * What List Plans needs from wherever plans are stored. Only reads, never changes a plan.
 */
public interface ListPlansUserDataAccessInterface {

    /** Returns every plan this user owns (empty if none). Throws if plans can't be reached. */
    List<WorkoutPlan> getPlansFor(UUID ownerId) throws PlanStorageUnavailableException;
}
