package use_case.list_plans;

import entity.BodyPart;
import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListPlansInteractorTest {
    private static final User USER = new User(UUID.randomUUID(), "jirat", "Jirat");
    private static final Exercise PULL_UP = new Exercise("0652", "Pull-up", null, BodyPart.BACK, "Lats",
            List.of(), "Body weight", null, List.of());

    private final FakeUserDataAccessObject userDataAccessObject = new FakeUserDataAccessObject();
    private final RecordingPresenter presenter = new RecordingPresenter();
    private CurrentUserDataAccessInterface currentUserDataAccessObject = () -> Optional.of(USER);

    private void run() {
        new ListPlansInteractor(userDataAccessObject, currentUserDataAccessObject, presenter).execute();
    }

    private static WorkoutPlan plan(String name, int exerciseCount) {
        List<PlannedExercise> exercises = new ArrayList<>();
        for (int i = 0; i < exerciseCount; i++) {
            exercises.add(new PlannedExercise(PULL_UP, 3, 8, null));
        }

        return new WorkoutPlan(UUID.randomUUID(), name, USER.getUserID(), exercises, false, false, null);
    }

    @Test
    void listsThePlansSortedByNameTest() {
        WorkoutPlan upper = plan("upper body A", 6);
        WorkoutPlan legs = plan("Leg day", 5);
        userDataAccessObject.plans = List.of(upper, legs);

        run();

        assertEquals(USER.getUserID(), userDataAccessObject.ownerAsked);
        List<ListPlansOutputData.PlanSummary> listed = presenter.output.getPlans();
        assertEquals(2, listed.size());

        // Sorted ignoring capitals, so "Leg day" comes before "upper body A"
        assertEquals("Leg day", listed.get(0).getName());
        assertEquals(5, listed.get(0).getExerciseCount());
        assertEquals(legs.getWorkoutPlanID(), listed.get(0).getPlanId());
        assertEquals("upper body A", listed.get(1).getName());
        assertNull(presenter.error);
    }

    @Test
    void noPlansIsStillASuccessTest() {
        run();

        assertTrue(presenter.output.getPlans().isEmpty());
        assertNull(presenter.error);
    }

    @Test
    void notLoggedInFailsTest() {
        currentUserDataAccessObject = Optional::empty;

        run();

        assertEquals("You need to be logged in to see your plans.", presenter.error);
        assertFalse(userDataAccessObject.called);
    }

    @Test
    void storageUnreachableFailsTest() {
        userDataAccessObject.unreachable = true;

        run();

        assertEquals("Couldn't reach your plans right now. Please try again.", presenter.error);
        assertNull(presenter.output);
    }

    private static class FakeUserDataAccessObject implements ListPlansUserDataAccessInterface {
        List<WorkoutPlan> plans = List.of();
        boolean unreachable;
        boolean called;
        UUID ownerAsked;

        @Override
        public List<WorkoutPlan> getPlansFor(UUID ownerId) throws PlanStorageUnavailableException {
            called = true;
            ownerAsked = ownerId;
            if (unreachable) {
                throw new PlanStorageUnavailableException("offline", null);
            }

            return plans;
        }
    }

    private static class RecordingPresenter implements ListPlansOutputBoundary {
        ListPlansOutputData output;
        String error;

        @Override
        public void prepareSuccessView(ListPlansOutputData outputData) {
            output = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }
}
