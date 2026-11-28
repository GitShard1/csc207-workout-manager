package use_case.start_workout;

import entity.ExerciseSet;
import entity.Workout;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;
import use_case.workout_common.SetEntry;
import use_case.workout_common.WorkoutStorageUnavailableException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static use_case.workout_common.WorkoutFixtures.BENCH_PRESS;
import static use_case.workout_common.WorkoutFixtures.SQUAT;
import static use_case.workout_common.WorkoutFixtures.USER;
import static use_case.workout_common.WorkoutFixtures.earlierWorkout;
import static use_case.workout_common.WorkoutFixtures.logged;
import static use_case.workout_common.WorkoutFixtures.pushDay;
import static use_case.workout_common.WorkoutFixtures.someoneElsesPlan;
import static use_case.workout_common.WorkoutFixtures.starterPlan;

class StartWorkoutInteractorTest {
    private final WorkoutPlan pushDay = pushDay();

    private final FakePlanDataAccessObject planDataAccessObject = new FakePlanDataAccessObject();
    private final FakeHistoryDataAccessObject historyDataAccessObject = new FakeHistoryDataAccessObject();
    private final RecordingPresenter presenter = new RecordingPresenter();
    private CurrentUserDataAccessInterface currentUserDataAccessObject = () -> Optional.of(USER);

    StartWorkoutInteractorTest() {
        planDataAccessObject.plans.put(pushDay.getWorkoutPlanID(), pushDay);
    }

    private void start(UUID planId) {
        new StartWorkoutInteractor(planDataAccessObject, historyDataAccessObject, currentUserDataAccessObject,
                presenter).execute(new StartWorkoutInputData(planId));
    }

    @Test
    void successTest() {
        historyDataAccessObject.workouts.add(earlierWorkout(LocalDate.of(2026, 11, 20),
                logged(BENCH_PRESS, new ExerciseSet(62.5, 8), new ExerciseSet(62.5, 7))));
        historyDataAccessObject.workouts.add(earlierWorkout(LocalDate.of(2026, 11, 13),
                logged(BENCH_PRESS, new ExerciseSet(60.0, 8)),
                logged(SQUAT, new ExerciseSet(100.0, 5), new ExerciseSet(100.0, 5))));

        start(pushDay.getWorkoutPlanID());

        assertEquals(new StartWorkoutOutputData(pushDay.getWorkoutPlanID(), "Push Day", List.of(
                new StartWorkoutOutputData.ExerciseEntry("0025", "Barbell Bench Press", "CHEST", "Barbell", 3, 8,
                        60.0, List.of(new SetEntry(62.5, 8), new SetEntry(62.5, 7)), LocalDate.of(2026, 11, 20)),
                new StartWorkoutOutputData.ExerciseEntry("0043", "Barbell Squat", "UPPER_LEGS", "Barbell", 5, 5,
                        null, List.of(new SetEntry(100.0, 5), new SetEntry(100.0, 5)),
                        LocalDate.of(2026, 11, 13)))),
                presenter.success);
        assertEquals(USER.getUserID(), historyDataAccessObject.requestedOwnerId);
        assertNull(presenter.error);
    }

    @Test
    void successNeverDoneTest() {
        start(pushDay.getWorkoutPlanID());

        assertEquals(List.of(), presenter.success.exercises().get(0).lastSets());
        assertEquals(List.of(), presenter.success.exercises().get(1).lastSets());
        assertNull(presenter.success.exercises().get(0).lastDate());
        assertNull(presenter.success.exercises().get(1).lastDate());
        assertNull(presenter.error);
    }

    @Test
    void successSkipsWorkoutWithoutSetsTest() {
        historyDataAccessObject.workouts.add(earlierWorkout(LocalDate.of(2026, 11, 20), logged(BENCH_PRESS)));
        historyDataAccessObject.workouts.add(earlierWorkout(LocalDate.of(2026, 11, 13),
                logged(BENCH_PRESS, new ExerciseSet(60.0, 8))));

        start(pushDay.getWorkoutPlanID());

        assertEquals(List.of(new SetEntry(60.0, 8)),
                presenter.success.exercises().get(0).lastSets());
        assertEquals(LocalDate.of(2026, 11, 13), presenter.success.exercises().get(0).lastDate());
        assertNull(presenter.error);
    }

    @Test
    void successStarterPlanTest() {
        WorkoutPlan starterPlan = starterPlan();
        planDataAccessObject.plans.put(starterPlan.getWorkoutPlanID(), starterPlan);

        start(starterPlan.getWorkoutPlanID());

        assertEquals("Beginner Full Body", presenter.success.planName());
        assertEquals(1, presenter.success.exercises().size());
        assertNull(presenter.error);
    }

    @Test
    void failureNotLoggedInTest() {
        currentUserDataAccessObject = Optional::empty;

        start(pushDay.getWorkoutPlanID());

        assertEquals("No user is logged in.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureNoPlanSelectedTest() {
        start(null);

        assertEquals("No plan selected.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failurePlanNotFoundTest() {
        start(UUID.randomUUID());

        assertEquals("Plan not found.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureSomeoneElsesPlanTest() {
        WorkoutPlan friendsPlan = someoneElsesPlan();
        planDataAccessObject.plans.put(friendsPlan.getWorkoutPlanID(), friendsPlan);

        start(friendsPlan.getWorkoutPlanID());

        assertEquals("You can only log workouts from your own plans.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failurePlansUnreachableTest() {
        planDataAccessObject.failGetById = true;

        start(pushDay.getWorkoutPlanID());

        assertEquals("Couldn't reach your plans right now. Please try again.", presenter.error);
        assertNull(presenter.success);
    }

    @Test
    void failureHistoryUnreachableTest() {
        historyDataAccessObject.failGetWorkouts = true;

        start(pushDay.getWorkoutPlanID());

        assertEquals("Couldn't load your past workouts right now. Please try again.", presenter.error);
        assertNull(presenter.success);
    }

    /** Stands in for plan storage. */
    private static class FakePlanDataAccessObject implements StartWorkoutPlanDataAccessInterface {
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

    /** Stands in for workout storage. Workouts are added newest first. */
    private static class FakeHistoryDataAccessObject implements StartWorkoutHistoryDataAccessInterface {
        private final List<Workout> workouts = new ArrayList<>();
        private boolean failGetWorkouts;
        private UUID requestedOwnerId;

        @Override
        public List<Workout> getWorkouts(UUID ownerId) throws WorkoutStorageUnavailableException {
            if (failGetWorkouts) {
                throw new WorkoutStorageUnavailableException("offline", null);
            }
            requestedOwnerId = ownerId;
            return workouts;
        }
    }

    /** Remembers which view the interactor asked for. */
    private static class RecordingPresenter implements StartWorkoutOutputBoundary {
        private StartWorkoutOutputData success;
        private String error;

        @Override
        public void prepareSuccessView(StartWorkoutOutputData outputData) {
            success = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }
}
