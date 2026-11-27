package use_case.open_plan;

import entity.BodyPart;
import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenPlanInteractorTest {
    private static final User USER = new User(UUID.randomUUID(), "amgalan", "Amgalan");
    private static final Exercise BENCH_PRESS = new Exercise("0025", "Barbell Bench Press", "A chest press.",
            BodyPart.CHEST, "Pectorals", List.of(), "Barbell", "compound", List.of());
    private static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", "A leg exercise.",
            BodyPart.UPPER_LEGS, "Quads", List.of(), "Barbell", "compound", List.of());

    private final WorkoutPlan pushDay = new WorkoutPlan(UUID.randomUUID(), "Push Day", USER.getUserID(),
            List.of(new PlannedExercise(BENCH_PRESS, 3, 8, 60.0), new PlannedExercise(SQUAT, 5, 5, null)),
            false, false, null);

    private final FakePlanDataAccessObject planDataAccessObject = new FakePlanDataAccessObject();
    private final RecordingPresenter presenter = new RecordingPresenter();
    private CurrentUserDataAccessInterface currentUserDataAccessObject = () -> Optional.of(USER);

    OpenPlanInteractorTest() {
        planDataAccessObject.plans.put(pushDay.getWorkoutPlanID(), pushDay);
    }

    private void open(UUID planId) {
        new OpenPlanInteractor(planDataAccessObject, currentUserDataAccessObject, presenter)
                .execute(new OpenPlanInputData(planId));
    }

    @Test
    void successTest() {
        open(pushDay.getWorkoutPlanID());

        assertEquals(pushDay.getWorkoutPlanID(), presenter.success.planId());
        assertEquals("Push Day", presenter.success.name());
        assertEquals(List.of(new OpenPlanOutputData.ExerciseEntry("0025", "Barbell Bench Press", 3, 8, 60.0),
                new OpenPlanOutputData.ExerciseEntry("0043", "Barbell Squat", 5, 5, null)),
                presenter.success.exercises());
        assertNull(presenter.error);
    }

    @Test
    void failureNotLoggedInTest() {
        currentUserDataAccessObject = Optional::empty;

        open(pushDay.getWorkoutPlanID());

        assertEquals("No user is logged in.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureNoPlanSelectedTest() {
        open(null);

        assertEquals("No plan selected.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failurePlanNotFoundTest() {
        open(UUID.randomUUID());

        assertEquals("Plan not found.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureStarterPlanTest() {
        WorkoutPlan starterPlan = new WorkoutPlan(UUID.randomUUID(), "Beginner Full Body", USER.getUserID(),
                List.of(new PlannedExercise(SQUAT, 3, 10, null)), false, true, null);
        planDataAccessObject.plans.put(starterPlan.getWorkoutPlanID(), starterPlan);

        open(starterPlan.getWorkoutPlanID());

        assertEquals("Starter plans can't be edited.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureSomeoneElsesPlanTest() {
        WorkoutPlan friendsPlan = new WorkoutPlan(UUID.randomUUID(), "Pull Day", UUID.randomUUID(),
                List.of(new PlannedExercise(SQUAT, 3, 10, null)), false, false, null);
        planDataAccessObject.plans.put(friendsPlan.getWorkoutPlanID(), friendsPlan);

        open(friendsPlan.getWorkoutPlanID());

        assertEquals("You can only edit your own plans.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureStorageDownTest() {
        planDataAccessObject.failGetById = true;

        open(pushDay.getWorkoutPlanID());

        assertEquals("Couldn't reach your plans right now. Please try again.", presenter.error);
        assertNull(presenter.success);
    }

    /** Stands in for plan storage. */
    private static class FakePlanDataAccessObject implements OpenPlanDataAccessInterface {
        private final Map<UUID, WorkoutPlan> plans = new HashMap<>();
        private boolean failGetById;

        @Override
        public WorkoutPlan getById(UUID planId) throws PlanStorageUnavailableException {
            if (failGetById) {
                throw new PlanStorageUnavailableException("offline", null);
            }
            return plans.get(planId);
        }
    }

    /** Remembers which view the interactor asked for. */
    private static class RecordingPresenter implements OpenPlanOutputBoundary {
        private OpenPlanOutputData success;
        private String error;

        @Override
        public void prepareSuccessView(OpenPlanOutputData outputData) {
            success = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }
}
