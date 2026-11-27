package use_case.plan_common;

import entity.Exercise;

public interface ExerciseLookupDataAccessInterface {
    /** Returns null when no exercise matches. */
    Exercise findById(String exerciseId);
}
