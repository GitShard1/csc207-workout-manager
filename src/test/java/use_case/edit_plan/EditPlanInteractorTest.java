package use_case.edit_plan;

import entity.BodyPart;
import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.ExerciseLookupDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPlanInteractorTest {
    private static final User USER = new User(UUID.randomUUID(), "amgalan", "Amgalan");
    private static final Exercise BENCH_PRESS = new Exercise("0025", "Barbell Bench Press", "A chest press.",
            BodyPart.CHEST, "Pectorals", List.of(), "Barbell", "compound", List.of());
    private static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", "A leg exercise.",
            BodyPart.UPPER_LEGS, "Quads", List.of(), "Barbell", "compound", List.of());

    private final WorkoutPlan pushDay = new WorkoutPlan(UUID.randomUUID(), "Push Day", USER.getUserID(),
            List.of(new PlannedExercise(BENCH_PRESS, 3, 8, 60.0)), false, false, null);

    private final FakePlanDataAccessObject planDataAccessObject = new FakePlanDataAccessObject();
    private final ExerciseLookupDataAccessInterface exerciseLookup =
            exerciseId -> Map.of("0025", BENCH_PRESS, "0043", SQUAT).get(exerciseId);
    private final RecordingPresenter presenter = new RecordingPresenter();
    private CurrentUserDataAccessInterface currentUserDataAccessObject = () -> Optional.of(USER);

    EditPlanInteractorTest() {
        planDataAccessObject.plans.put(pushDay.getWorkoutPlanID(), pushDay);
    }

    private EditPlanInteractor interactor() {
        return new EditPlanInteractor(planDataAccessObject, exerciseLookup, currentUserDataAccessObject, presenter);
    }

    private void save(UUID planId, String name, List<EditPlanInputData.ExerciseEntry> exercises) {
        interactor().execute(new EditPlanInputData(planId, name, exercises));
    }

    private static EditPlanInputData.ExerciseEntry entry(String exerciseId, int sets, int reps, Double weight) {
        return new EditPlanInputData.ExerciseEntry(exerciseId, sets, reps, weight);
    }

    @Test
    void loadShowsThePlanTest() {
        interactor().loadPlan(pushDay.getWorkoutPlanID());

        assertEquals(pushDay.getWorkoutPlanID(), presenter.success.planId());
        assertEquals("Push Day", presenter.success.name());
        assertEquals(List.of(new EditPlanOutputData.ExerciseEntry("0025", "Barbell Bench Press", 3, 8, 60.0)),
                presenter.success.exercises());
        assertNull(presenter.error);
    }

    @Test
    void successTest() {
        // Rename, reorder (squat first), change bench press targets and drop its weight
        save(pushDay.getWorkoutPlanID(), "Full Body", List.of(entry("0043", 5, 5, 100.0), entry("0025", 4, 10, null)));

        WorkoutPlan saved = planDataAccessObject.updated;
        assertEquals(pushDay.getWorkoutPlanID(), saved.getWorkoutPlanID());
        assertEquals("Full Body", saved.getName());
        assertEquals(USER.getUserID(), saved.getUserOwnerID());
        assertSame(SQUAT, saved.getExercises().get(0).getExercise());
        assertSame(BENCH_PRESS, saved.getExercises().get(1).getExercise());
        assertEquals(4, saved.getExercises().get(1).getTargetSets());
        assertEquals(10, saved.getExercises().get(1).getTargetReps());
        assertNull(saved.getExercises().get(1).getTargetWeight());

        assertEquals("Full Body", presenter.success.name());
        assertEquals(List.of(new EditPlanOutputData.ExerciseEntry("0043", "Barbell Squat", 5, 5, 100.0),
                new EditPlanOutputData.ExerciseEntry("0025", "Barbell Bench Press", 4, 10, null)),
                presenter.success.exercises());
        assertNull(presenter.error);
    }

    @Test
    void keepingTheSameNameIsAllowedTest() {
        // The plan's own name counts as taken, but it is still this plan's name, even with different case
        planDataAccessObject.takenNames.add("push day");

        save(pushDay.getWorkoutPlanID(), "PUSH DAY", List.of(entry("0025", 5, 5, 70.0)));

        assertEquals("PUSH DAY", planDataAccessObject.updated.getName());
        assertNull(presenter.error);
    }

    @Test
    void copiedPlanKeepsWhereItCameFromTest() {
        UUID friendId = UUID.randomUUID();
        WorkoutPlan copiedPlan = new WorkoutPlan(UUID.randomUUID(), "Leg Day", USER.getUserID(),
                List.of(new PlannedExercise(SQUAT, 3, 5, 80.0)), true, false, friendId);
        planDataAccessObject.plans.put(copiedPlan.getWorkoutPlanID(), copiedPlan);

        save(copiedPlan.getWorkoutPlanID(), "Leg Day", List.of(entry("0043", 4, 5, 85.0)));

        assertEquals(friendId, planDataAccessObject.updated.getCopiedFromUserID());
        assertTrue(planDataAccessObject.updated.isWorkoutCompleted());
    }

    @Test
    void deleteRemovesThePlanTest() {
        interactor().deletePlan(pushDay.getWorkoutPlanID());

        assertEquals(pushDay.getWorkoutPlanID(), planDataAccessObject.deletedId);
        assertTrue(presenter.deleted);
        assertNull(presenter.error);
    }

    @Test
    void failureEmptyNameTest() {
        save(pushDay.getWorkoutPlanID(), "", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("Plan name is required.", presenter.error);
        assertNull(planDataAccessObject.updated);
    }

    @Test
    void failureNoExercisesTest() {
        save(pushDay.getWorkoutPlanID(), "Push Day", List.of());

        assertEquals("Add at least one exercise.", presenter.error);
        assertNull(planDataAccessObject.updated);
    }

    @Test
    void failureInvalidRowTest() {
        save(pushDay.getWorkoutPlanID(), "Push Day", List.of(entry("0025", 3, 8, 60.0), entry("0043", 0, 5, null)));

        assertEquals("Row 2: must contain at least 1 set.", presenter.error);
        assertNull(planDataAccessObject.updated);
    }

    @Test
    void failureNameTakenTest() {
        planDataAccessObject.takenNames.add("leg day");

        save(pushDay.getWorkoutPlanID(), "Leg Day", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("You already have a plan called \"Leg Day\".", presenter.error);
        assertNull(planDataAccessObject.updated);
    }

    @Test
    void failureExerciseNotFoundTest() {
        save(pushDay.getWorkoutPlanID(), "Push Day", List.of(entry("0025", 3, 8, 60.0), entry("9999", 3, 8, null)));

        assertEquals("Row 2: exercise not found.", presenter.error);
        assertNull(planDataAccessObject.updated);
    }

    @Test
    void failureNotLoggedInTest() {
        currentUserDataAccessObject = Optional::empty;

        interactor().loadPlan(pushDay.getWorkoutPlanID());

        assertEquals("No user is logged in.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureNoPlanSelectedTest() {
        save(null, "Push Day", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("No plan selected.", presenter.error);
        assertNull(planDataAccessObject.updated);
    }

    @Test
    void failurePlanNotFoundTest() {
        interactor().deletePlan(UUID.randomUUID());

        assertEquals("Plan not found.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
    }

    @Test
    void failureStarterPlanTest() {
        WorkoutPlan starterPlan = new WorkoutPlan(UUID.randomUUID(), "Beginner Full Body", USER.getUserID(),
                List.of(new PlannedExercise(SQUAT, 3, 10, null)), false, true, null);
        planDataAccessObject.plans.put(starterPlan.getWorkoutPlanID(), starterPlan);

        interactor().deletePlan(starterPlan.getWorkoutPlanID());

        assertEquals("Starter plans can't be edited.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
    }

    @Test
    void failureSomeoneElsesPlanTest() {
        WorkoutPlan friendsPlan = new WorkoutPlan(UUID.randomUUID(), "Pull Day", UUID.randomUUID(),
                List.of(new PlannedExercise(SQUAT, 3, 10, null)), false, false, null);
        planDataAccessObject.plans.put(friendsPlan.getWorkoutPlanID(), friendsPlan);

        save(friendsPlan.getWorkoutPlanID(), "Mine Now", List.of(entry("0043", 3, 10, null)));

        assertEquals("You can only edit your own plans.", presenter.error);
        assertNull(planDataAccessObject.updated);
        assertFalse(presenter.deleted);
    }

    @Test
    void failureStorageDownWhenLoadingTest() {
        planDataAccessObject.failGetById = true;

        interactor().loadPlan(pushDay.getWorkoutPlanID());

        assertEquals("Couldn't reach your plans right now. Please try again.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureStorageDownWhenCheckingNameTest() {
        planDataAccessObject.failExistsByName = true;

        save(pushDay.getWorkoutPlanID(), "Full Body", List.of(entry("0025", 3, 8, 60.0)));

        assertEquals("Your changes weren't saved. Please try again.", presenter.error);
        assertNull(planDataAccessObject.updated);
    }

    @Test
    void keepingTheSameNameSkipsTheNameCheckTest() {
        planDataAccessObject.failExistsByName = true;

        save(pushDay.getWorkoutPlanID(), "Push Day", List.of(entry("0025", 4, 8, 60.0)));

        assertNotNull(planDataAccessObject.updated);
        assertNull(presenter.error);
    }

    @Test
    void failureUpdateFailsTest() {
        planDataAccessObject.failUpdate = true;

        save(pushDay.getWorkoutPlanID(), "Push Day", List.of(entry("0025", 4, 8, 60.0)));

        assertEquals("Your changes weren't saved. Please try again.", presenter.error);
        assertNull(planDataAccessObject.updated);
        assertNull(presenter.success);
    }

    @Test
    void failureDeleteFailsTest() {
        planDataAccessObject.failDelete = true;

        interactor().deletePlan(pushDay.getWorkoutPlanID());

        assertEquals("Your plan wasn't deleted. Please try again.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
        assertFalse(presenter.deleted);
    }

    /** Stands in for plan storage. Remembers what was updated or deleted. */
    private static class FakePlanDataAccessObject implements EditPlanDataAccessInterface {
        private final Map<UUID, WorkoutPlan> plans = new HashMap<>();
        private final List<String> takenNames = new ArrayList<>();
        private WorkoutPlan updated;
        private UUID deletedId;
        private boolean failGetById;
        private boolean failExistsByName;
        private boolean failUpdate;
        private boolean failDelete;

        @Override
        public WorkoutPlan getById(UUID planId) throws PlanStorageUnavailableException {
            if (failGetById) {
                throw new PlanStorageUnavailableException("offline", null);
            }
            return plans.get(planId);
        }

        @Override
        public boolean existsByName(UUID ownerId, String planName) throws PlanStorageUnavailableException {
            if (failExistsByName) {
                throw new PlanStorageUnavailableException("offline", null);
            }
            return takenNames.contains(planName.toLowerCase());
        }

        @Override
        public void update(WorkoutPlan workoutPlan) throws PlanStorageUnavailableException {
            if (failUpdate) {
                throw new PlanStorageUnavailableException("offline", null);
            }
            updated = workoutPlan;
        }

        @Override
        public void delete(UUID planId) throws PlanStorageUnavailableException {
            if (failDelete) {
                throw new PlanStorageUnavailableException("offline", null);
            }
            deletedId = planId;
        }
    }

    /** Remembers which view the interactor asked for. */
    private static class RecordingPresenter implements EditPlanOutputBoundary {
        private EditPlanOutputData success;
        private boolean deleted;
        private String error;

        @Override
        public void prepareSuccessView(EditPlanOutputData outputData) {
            success = outputData;
        }

        @Override
        public void prepareDeleteSuccessView() {
            deleted = true;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }
}
