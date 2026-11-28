package use_case.create_plan;

import entity.BodyPart;
import entity.Exercise;
import entity.User;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.ExerciseLookupDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CreatePlanInteractorTest {
    private static final User USER = new User(UUID.randomUUID(), "amgalan", "Amgalan");
    private static final Exercise BENCH_PRESS = new Exercise("0025", "Barbell Bench Press", "A chest press.",
            BodyPart.CHEST, "Pectorals", List.of(), "Barbell", "compound", List.of());
    private static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", "A leg exercise.",
            BodyPart.UPPER_LEGS, "Quads", List.of(), "Barbell", "compound", List.of());

    private final FakePlanDataAccessObject planDataAccessObject = new FakePlanDataAccessObject();
    private final ExerciseLookupDataAccessInterface exerciseLookup =
            exerciseId -> Map.of("0025", BENCH_PRESS, "0043", SQUAT).get(exerciseId);
    private final RecordingPresenter presenter = new RecordingPresenter();
    private CurrentUserDataAccessInterface currentUserDataAccessObject = () -> Optional.of(USER);

    private CreatePlanInteractor interactor() {
        return new CreatePlanInteractor(planDataAccessObject, exerciseLookup, currentUserDataAccessObject, presenter);
    }

    private void create(String name, List<CreatePlanInputData.ExerciseEntry> exercises) {
        interactor().execute(new CreatePlanInputData(name, exercises));
    }

    private static CreatePlanInputData.ExerciseEntry entry(String exerciseId, int sets, int reps, Double weight) {
        return new CreatePlanInputData.ExerciseEntry(exerciseId, sets, reps, weight);
    }

    @Test
    void successTest() {
        create("Push Day", List.of(entry("0025", 3, 8, 60.0), entry("0043", 5, 5, null)));

        WorkoutPlan saved = planDataAccessObject.saved;
        assertNotNull(saved.getWorkoutPlanID());
        assertEquals("Push Day", saved.getName());
        assertEquals(USER.getUserID(), saved.getUserOwnerID());
        assertEquals(2, saved.getExercises().size());
        assertSame(BENCH_PRESS, saved.getExercises().get(0).getExercise());
        assertEquals(3, saved.getExercises().get(0).getTargetSets());
        assertEquals(8, saved.getExercises().get(0).getTargetReps());
        assertEquals(60.0, saved.getExercises().get(0).getTargetWeight());
        assertSame(SQUAT, saved.getExercises().get(1).getExercise());
        assertEquals(5, saved.getExercises().get(1).getTargetSets());
        assertEquals(5, saved.getExercises().get(1).getTargetReps());
        assertNull(saved.getExercises().get(1).getTargetWeight());
        assertFalse(saved.isPreset());
        assertFalse(saved.isWorkoutCompleted());
        assertNull(saved.getCopiedFromUserID());

        assertEquals(saved.getWorkoutPlanID(), presenter.success.planId());
        assertEquals("Push Day", presenter.success.name());
        assertEquals(2, presenter.success.exerciseCount());
        assertNull(presenter.error);
    }

    @Test
    void nameOfExactlyFiftyCharactersIsAllowedTest() {
        String name = "a".repeat(50);

        create(name, List.of(entry("0025", 3, 8, 60.0)));

        assertEquals(name, planDataAccessObject.saved.getName());
        assertNull(presenter.error);
    }

    @Test
    void failureNotLoggedInTest() {
        currentUserDataAccessObject = Optional::empty;

        create("Push Day", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("No user is logged in.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureEmptyNameTest() {
        create("", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("Plan name is required.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureNameTooLongTest() {
        create("a".repeat(51), List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("Plan name must be at most 50 characters.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureNoExercisesTest() {
        create("Push Day", List.of());

        assertEquals("Add at least one exercise.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureNoExerciseChosenTest() {
        create("Push Day", List.of(entry("", 3, 8, null)));

        assertEquals("Row 1: choose an exercise.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureNoSetsTest() {
        create("Push Day", List.of(entry("0025", 3, 8, 60.0), entry("0043", 0, 5, null)));

        assertEquals("Row 2: must contain at least 1 set.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureNoRepsTest() {
        create("Push Day", List.of(entry("0025", 3, 0, null)));

        assertEquals("Row 1: must contain at least 1 rep.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureNegativeWeightTest() {
        create("Push Day", List.of(entry("0025", 3, 8, -5.0)));

        assertEquals("Row 1: weights cannot be negative.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureWeightNotANumberTest() {
        create("Push Day", List.of(entry("0025", 3, 8, Double.NaN)));
        assertEquals("Row 1: weight must be a number.", presenter.error);

        create("Push Day", List.of(entry("0025", 3, 8, Double.POSITIVE_INFINITY)));
        assertEquals("Row 1: weight must be a number.", presenter.error);

        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureNameTakenTest() {
        planDataAccessObject.takenNames.add("push day");

        create("Push Day", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("You already have a plan called \"Push Day\".", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureExerciseNotFoundTest() {
        create("Push Day", List.of(entry("0025", 3, 8, 60.0), entry("9999", 3, 8, null)));

        assertEquals("Row 2: exercise not found.", presenter.error);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureSaveFailsTest() {
        planDataAccessObject.failSave = true;

        create("Push Day", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("Your plan wasn't saved. Please try again.", presenter.error);
        assertNull(presenter.success);
        assertNull(planDataAccessObject.saved);
    }

    @Test
    void failureNameCheckFailsTest() {
        planDataAccessObject.failExistsByName = true;

        create("Push Day", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("Your plan wasn't saved. Please try again.", presenter.error);
        assertNull(presenter.success);
        assertNull(planDataAccessObject.saved);
    }

    /** Stands in for plan storage. Remembers what was saved. */
    private static class FakePlanDataAccessObject implements CreatePlanDataAccessInterface {
        private final List<String> takenNames = new ArrayList<>();
        private WorkoutPlan saved;
        private boolean failSave;
        private boolean failExistsByName;

        @Override
        public boolean existsByName(UUID ownerId, String planName) throws PlanStorageUnavailableException {
            if (failExistsByName) {
                throw new PlanStorageUnavailableException("offline", null);
            }
            return takenNames.contains(planName.toLowerCase());
        }

        @Override
        public void save(WorkoutPlan workoutPlan) throws PlanStorageUnavailableException {
            if (failSave) {
                throw new PlanStorageUnavailableException("offline", null);
            }
            saved = workoutPlan;
        }
    }

    /** Remembers which view the interactor asked for. */
    private static class RecordingPresenter implements CreatePlanOutputBoundary {
        private CreatePlanOutputData success;
        private String error;

        @Override
        public void prepareSuccessView(CreatePlanOutputData outputData) {
            success = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }
}
