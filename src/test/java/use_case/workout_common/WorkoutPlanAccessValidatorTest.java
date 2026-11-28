package use_case.workout_common;

import entity.BodyPart;
import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkoutPlanAccessValidatorTest {
    private static final User USER = new User(UUID.randomUUID(), "amgalan", "Amgalan");
    private static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", "A leg exercise.",
            BodyPart.UPPER_LEGS, "Quads", List.of(), "Barbell", "compound", List.of());
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
        WorkoutPlan starterPlan = new WorkoutPlan(UUID.randomUUID(), "Beginner Full Body", UUID.randomUUID(),
                EXERCISES, false, true, null);

        assertEquals("", WorkoutPlanAccessValidator.checkStartable(USER, starterPlan));
    }

    @Test
    void failurePlanNotFoundTest() {
        assertEquals("Plan not found.", WorkoutPlanAccessValidator.checkStartable(USER, null));
    }

    @Test
    void failureSomeoneElsesPlanTest() {
        WorkoutPlan friendsPlan = new WorkoutPlan(UUID.randomUUID(), "Pull Day", UUID.randomUUID(), EXERCISES,
                false, false, null);

        assertEquals("You can only log workouts from your own plans.",
                WorkoutPlanAccessValidator.checkStartable(USER, friendsPlan));
    }
}
