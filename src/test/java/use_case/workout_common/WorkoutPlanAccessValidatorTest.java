package use_case.workout_common;

import entity.PlannedExercise;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static use_case.workout_common.WorkoutFixtures.SQUAT;
import static use_case.workout_common.WorkoutFixtures.USER;
import static use_case.workout_common.WorkoutFixtures.someoneElsesPlan;
import static use_case.workout_common.WorkoutFixtures.starterPlan;

class WorkoutPlanAccessValidatorTest {
    private static final List<PlannedExercise> EXERCISES = List.of(new PlannedExercise(SQUAT, 5, 5, 100.0));

    @Test
    void ownPlanTest() {
        UUID ownerIdFromStorage = UUID.fromString(USER.getUserID().toString());
        WorkoutPlan ownPlan = new WorkoutPlan(UUID.randomUUID(), "Leg Day", ownerIdFromStorage, EXERCISES,
                false, false, null);

        assertEquals("", WorkoutPlanAccessValidator.checkStartable(USER, ownPlan));
    }

    @Test
    void starterPlanTest() {
        WorkoutPlan starterPlan = starterPlan();

        assertEquals("", WorkoutPlanAccessValidator.checkStartable(USER, starterPlan));
    }

    @Test
    void failurePlanNotFoundTest() {
        assertEquals("Plan not found.", WorkoutPlanAccessValidator.checkStartable(USER, null));
    }

    @Test
    void failureSomeoneElsesPlanTest() {
        WorkoutPlan friendsPlan = someoneElsesPlan();

        assertEquals("You can only log workouts from your own plans.",
                WorkoutPlanAccessValidator.checkStartable(USER, friendsPlan));
    }
}
