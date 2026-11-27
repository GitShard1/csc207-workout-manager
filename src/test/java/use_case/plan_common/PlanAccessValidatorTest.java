package use_case.plan_common;

import entity.BodyPart;
import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanAccessValidatorTest {
    private static final User USER = new User(UUID.randomUUID(), "amgalan", "Amgalan");
    private static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", "A leg exercise.",
            BodyPart.UPPER_LEGS, "Quads", List.of(), "Barbell", "compound", List.of());

    private static WorkoutPlan plan(UUID ownerId, boolean preset) {
        return new WorkoutPlan(UUID.randomUUID(), "Leg Day", ownerId,
                List.of(new PlannedExercise(SQUAT, 3, 5, 80.0)), false, preset, null);
    }

    @Test
    void ownPlanIsAllowedTest() {
        assertEquals("", PlanAccessValidator.checkEditable(USER, plan(USER.getUserID(), false)));
    }

    @Test
    void missingPlanTest() {
        assertEquals("Plan not found.", PlanAccessValidator.checkEditable(USER, null));
    }

    @Test
    void starterPlanTest() {
        assertEquals("Starter plans can't be edited.",
                PlanAccessValidator.checkEditable(USER, plan(USER.getUserID(), true)));
    }

    @Test
    void someoneElsesPlanTest() {
        assertEquals("You can only edit your own plans.",
                PlanAccessValidator.checkEditable(USER, plan(UUID.randomUUID(), false)));
    }

    @Test
    void starterCheckComesBeforeOwnerCheckTest() {
        assertEquals("Starter plans can't be edited.",
                PlanAccessValidator.checkEditable(USER, plan(UUID.randomUUID(), true)));
    }
}
