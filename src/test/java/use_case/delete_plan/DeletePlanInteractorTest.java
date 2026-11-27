package use_case.delete_plan;

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

class DeletePlanInteractorTest {
    private static final User USER = new User(UUID.randomUUID(), "amgalan", "Amgalan");
    private static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", "A leg exercise.",
            BodyPart.UPPER_LEGS, "Quads", List.of(), "Barbell", "compound", List.of());

    private final WorkoutPlan pushDay = new WorkoutPlan(UUID.randomUUID(), "Push Day", USER.getUserID(),
            List.of(new PlannedExercise(SQUAT, 3, 8, 60.0)), false, false, null);

    private final FakePlanDataAccessObject planDataAccessObject = new FakePlanDataAccessObject();
    private final RecordingPresenter presenter = new RecordingPresenter();
    private CurrentUserDataAccessInterface currentUserDataAccessObject = () -> Optional.of(USER);

    DeletePlanInteractorTest() {
        planDataAccessObject.plans.put(pushDay.getWorkoutPlanID(), pushDay);
    }

    private void delete(UUID planId) {
        new DeletePlanInteractor(planDataAccessObject, currentUserDataAccessObject, presenter)
                .execute(new DeletePlanInputData(planId));
    }

    @Test
    void successTest() {
        delete(pushDay.getWorkoutPlanID());

        assertEquals(pushDay.getWorkoutPlanID(), planDataAccessObject.deletedId);
        assertEquals(pushDay.getWorkoutPlanID(), presenter.success.planId());
        assertEquals("Push Day", presenter.success.name());
        assertNull(presenter.error);
    }

    @Test
    void failureNotLoggedInTest() {
        currentUserDataAccessObject = Optional::empty;

        delete(pushDay.getWorkoutPlanID());

        assertEquals("No user is logged in.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
        assertNull(presenter.success);
    }

    @Test
    void failureNoPlanSelectedTest() {
        delete(null);

        assertEquals("No plan selected.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
        assertNull(presenter.success);
    }

    @Test
    void failurePlanNotFoundTest() {
        delete(UUID.randomUUID());

        assertEquals("Plan not found.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
        assertNull(presenter.success);
    }

    @Test
    void failureStarterPlanTest() {
        WorkoutPlan starterPlan = new WorkoutPlan(UUID.randomUUID(), "Beginner Full Body", USER.getUserID(),
                List.of(new PlannedExercise(SQUAT, 3, 10, null)), false, true, null);
        planDataAccessObject.plans.put(starterPlan.getWorkoutPlanID(), starterPlan);

        delete(starterPlan.getWorkoutPlanID());

        assertEquals("Starter plans can't be edited.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
        assertNull(presenter.success);
    }

    @Test
    void failureSomeoneElsesPlanTest() {
        WorkoutPlan friendsPlan = new WorkoutPlan(UUID.randomUUID(), "Pull Day", UUID.randomUUID(),
                List.of(new PlannedExercise(SQUAT, 3, 10, null)), false, false, null);
        planDataAccessObject.plans.put(friendsPlan.getWorkoutPlanID(), friendsPlan);

        delete(friendsPlan.getWorkoutPlanID());

        assertEquals("You can only edit your own plans.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
        assertNull(presenter.success);
    }

    @Test
    void failureStorageDownWhenLoadingTest() {
        planDataAccessObject.failGetById = true;

        delete(pushDay.getWorkoutPlanID());

        assertEquals("Couldn't reach your plans right now. Please try again.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
        assertNull(presenter.success);
    }

    @Test
    void failureDeleteFailsTest() {
        planDataAccessObject.failDelete = true;

        delete(pushDay.getWorkoutPlanID());

        assertEquals("Your plan wasn't deleted. Please try again.", presenter.error);
        assertNull(planDataAccessObject.deletedId);
        assertNull(presenter.success);
    }

    /** Stands in for plan storage. Remembers what was deleted. */
    private static class FakePlanDataAccessObject implements DeletePlanDataAccessInterface {
        private final Map<UUID, WorkoutPlan> plans = new HashMap<>();
        private UUID deletedId;
        private boolean failGetById;
        private boolean failDelete;

        @Override
        public WorkoutPlan getById(UUID planId) throws PlanStorageUnavailableException {
            if (failGetById) {
                throw new PlanStorageUnavailableException("offline", null);
            }
            return plans.get(planId);
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
    private static class RecordingPresenter implements DeletePlanOutputBoundary {
        private DeletePlanOutputData success;
        private String error;

        @Override
        public void prepareSuccessView(DeletePlanOutputData outputData) {
            success = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }
}
