package use_case.finish_workout;

import entity.ExerciseSet;
import entity.PlannedExercise;
import entity.Workout;
import entity.WorkoutPlan;
import org.junit.jupiter.api.Test;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;
import use_case.workout_common.SetEntry;
import use_case.workout_common.WorkoutStorageUnavailableException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static use_case.workout_common.WorkoutFixtures.BENCH_PRESS;
import static use_case.workout_common.WorkoutFixtures.SQUAT;
import static use_case.workout_common.WorkoutFixtures.USER;
import static use_case.workout_common.WorkoutFixtures.earlierWorkout;
import static use_case.workout_common.WorkoutFixtures.logged;
import static use_case.workout_common.WorkoutFixtures.pushDay;
import static use_case.workout_common.WorkoutFixtures.someoneElsesPlan;
import static use_case.workout_common.WorkoutFixtures.starterPlan;

class FinishWorkoutInteractorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    private final WorkoutPlan pushDay = pushDay();

    private final FakePlanDataAccessObject planDataAccessObject = new FakePlanDataAccessObject();
    private final FakeWorkoutDataAccessObject workoutDataAccessObject = new FakeWorkoutDataAccessObject();
    private final RecordingPresenter presenter = new RecordingPresenter();
    private CurrentUserDataAccessInterface currentUserDataAccessObject = () -> Optional.of(USER);

    FinishWorkoutInteractorTest() {
        planDataAccessObject.plans.put(pushDay.getWorkoutPlanID(), pushDay);
    }

    private void finish(UUID planId, List<FinishWorkoutInputData.ExerciseEntry> exercises) {
        new FinishWorkoutInteractor(planDataAccessObject, workoutDataAccessObject, currentUserDataAccessObject,
                CLOCK, presenter).execute(new FinishWorkoutInputData(planId, exercises));
    }

    private void finishPushDay(FinishWorkoutInputData.ExerciseEntry... exercises) {
        finish(pushDay.getWorkoutPlanID(), List.of(exercises));
    }

    private static FinishWorkoutInputData.ExerciseEntry entry(String exerciseId,
                                                              SetEntry... sets) {
        return new FinishWorkoutInputData.ExerciseEntry(exerciseId, List.of(sets));
    }

    private static SetEntry set(double weight, int reps) {
        return new SetEntry(weight, reps);
    }

    private void assertNothingSaved() {
        assertTrue(workoutDataAccessObject.saved.isEmpty());
        assertNull(presenter.success);
    }

    @Test
    void successTest() {
        finishPushDay(entry("0025", set(62.5, 8), set(62.5, 7)), entry("0043", set(100.0, 5)));

        assertEquals(1, workoutDataAccessObject.saved.size());
        Workout saved = workoutDataAccessObject.saved.get(0);
        assertNotNull(saved.getWorkoutID());
        assertEquals(USER.getUserID(), saved.getUserOwnerID());
        assertEquals(pushDay.getWorkoutPlanID(), saved.getPlanID());
        assertEquals("Push Day", saved.getPlanName());
        assertEquals(TODAY, saved.getDate());
        assertEquals(2, saved.getExercises().size());
        assertEquals(BENCH_PRESS, saved.getExercises().get(0).getExercise());
        assertEquals(2, saved.getExercises().get(0).getCompletedSets().size());
        assertEquals(62.5, saved.getExercises().get(0).getCompletedSets().get(1).getWeight());
        assertEquals(7, saved.getExercises().get(0).getCompletedSets().get(1).getReps());
        assertEquals(SQUAT, saved.getExercises().get(1).getExercise());

        assertEquals(new FinishWorkoutOutputData("Push Day", TODAY, List.of(
                new FinishWorkoutOutputData.ExerciseSummary("Barbell Bench Press", List.of(
                        new SetEntry(62.5, 8),
                        new SetEntry(62.5, 7))),
                new FinishWorkoutOutputData.ExerciseSummary("Barbell Squat", List.of(
                        new SetEntry(100.0, 5)))),
                List.of()), presenter.success);
        assertEquals(USER.getUserID(), workoutDataAccessObject.requestedOwnerId);
        assertNull(presenter.error);
        assertEquals(0, presenter.noSetsCalls);
    }

    @Test
    void successSkippedExerciseNotSavedTest() {
        finishPushDay(entry("0025", set(62.5, 8)), entry("0043"));

        Workout saved = workoutDataAccessObject.saved.get(0);
        assertEquals(1, saved.getExercises().size());
        assertEquals(BENCH_PRESS, saved.getExercises().get(0).getExercise());
        assertEquals(1, presenter.success.exercises().size());
        assertEquals("Barbell Bench Press", presenter.success.exercises().get(0).exerciseName());
    }

    @Test
    void successNewPersonalRecordTest() {
        workoutDataAccessObject.history.add(earlierWorkout(LocalDate.of(2026, 11, 20),
                logged(BENCH_PRESS, new ExerciseSet(60.0, 8), new ExerciseSet(57.5, 8))));

        finishPushDay(entry("0025", set(60.0, 8), set(62.5, 6)));

        assertEquals(List.of(new FinishWorkoutOutputData.PersonalRecordEntry("Barbell Bench Press", 62.5, 60.0)),
                presenter.success.personalRecords());
    }

    @Test
    void successPersonalRecordChecksEveryEarlierWorkoutTest() {
        workoutDataAccessObject.history.add(earlierWorkout(LocalDate.of(2026, 11, 20),
                logged(BENCH_PRESS, new ExerciseSet(55.0, 8))));
        workoutDataAccessObject.history.add(earlierWorkout(LocalDate.of(2026, 11, 13),
                logged(BENCH_PRESS, new ExerciseSet(70.0, 3))));

        finishPushDay(entry("0025", set(65.0, 5)));

        assertEquals(List.of(), presenter.success.personalRecords());
    }

    @Test
    void successNoRecordWhenEqualTest() {
        workoutDataAccessObject.history.add(earlierWorkout(LocalDate.of(2026, 11, 20),
                logged(BENCH_PRESS, new ExerciseSet(62.5, 8))));

        finishPushDay(entry("0025", set(62.5, 10)));

        assertEquals(List.of(), presenter.success.personalRecords());
    }

    @Test
    void successNoRecordFirstTimeTest() {
        workoutDataAccessObject.history.add(earlierWorkout(LocalDate.of(2026, 11, 20),
                logged(SQUAT, new ExerciseSet(100.0, 5))));

        finishPushDay(entry("0025", set(62.5, 8)));

        assertEquals(List.of(), presenter.success.personalRecords());
    }

    @Test
    void successNoRecordBodyweightTest() {
        workoutDataAccessObject.history.add(earlierWorkout(LocalDate.of(2026, 11, 20),
                logged(BENCH_PRESS, new ExerciseSet(0.0, 10))));

        finishPushDay(entry("0025", set(0.0, 15)));

        assertEquals(List.of(), presenter.success.personalRecords());
    }

    @Test
    void successSameExerciseTwiceOneRecordTest() {
        WorkoutPlan doubleBench = new WorkoutPlan(UUID.randomUUID(), "Bench Focus", USER.getUserID(),
                List.of(new PlannedExercise(BENCH_PRESS, 3, 5, 70.0), new PlannedExercise(BENCH_PRESS, 3, 10, 50.0)),
                false, false, null);
        planDataAccessObject.plans.put(doubleBench.getWorkoutPlanID(), doubleBench);
        workoutDataAccessObject.history.add(earlierWorkout(LocalDate.of(2026, 11, 20),
                logged(BENCH_PRESS, new ExerciseSet(60.0, 8))));

        finish(doubleBench.getWorkoutPlanID(), List.of(entry("0025", set(65.0, 5)), entry("0025", set(50.0, 10))));

        assertEquals(2, workoutDataAccessObject.saved.get(0).getExercises().size());
        assertEquals(List.of(new FinishWorkoutOutputData.PersonalRecordEntry("Barbell Bench Press", 65.0, 60.0)),
                presenter.success.personalRecords());
    }

    @Test
    void successStarterPlanTest() {
        WorkoutPlan starterPlan = starterPlan();
        planDataAccessObject.plans.put(starterPlan.getWorkoutPlanID(), starterPlan);

        finish(starterPlan.getWorkoutPlanID(), List.of(entry("0043", set(40.0, 10))));

        assertEquals(starterPlan.getWorkoutPlanID(), workoutDataAccessObject.saved.get(0).getPlanID());
        assertEquals(USER.getUserID(), workoutDataAccessObject.saved.get(0).getUserOwnerID());
        assertNull(presenter.error);
    }

    @Test
    void noSetsTest() {
        finishPushDay(entry("0025"), entry("0043"));

        assertEquals(1, presenter.noSetsCalls);
        assertNull(presenter.error);
        assertNothingSaved();
    }

    @Test
    void noSetsNullListTest() {
        finish(pushDay.getWorkoutPlanID(), null);

        assertEquals(1, presenter.noSetsCalls);
        assertNull(presenter.error);
        assertNothingSaved();
    }

    @Test
    void noSetsNullSetsTest() {
        finishPushDay(new FinishWorkoutInputData.ExerciseEntry("0025", null));

        assertEquals(1, presenter.noSetsCalls);
        assertNothingSaved();
    }

    @Test
    void failureNotLoggedInTest() {
        currentUserDataAccessObject = Optional::empty;

        finishPushDay(entry("0025", set(62.5, 8)));

        assertEquals("No user is logged in.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failureNoPlanSelectedTest() {
        finish(null, List.of(entry("0025", set(62.5, 8))));

        assertEquals("No plan selected.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failureInvalidSetTest() {
        finishPushDay(entry("0025", set(62.5, 8)), entry("0043", set(-5.0, 5)));

        assertEquals("Exercise 2, set 1: Weight can't be negative.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failureInvalidSetCountsFromOneTest() {
        finishPushDay(entry("0025", set(62.5, 8), set(62.5, 8), set(62.5, 0)));

        assertEquals("Exercise 1, set 3: Reps must be at least 1.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failurePlanNotFoundTest() {
        finish(UUID.randomUUID(), List.of(entry("0025", set(62.5, 8))));

        assertEquals("Plan not found.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failureSomeoneElsesPlanTest() {
        WorkoutPlan friendsPlan = someoneElsesPlan();
        planDataAccessObject.plans.put(friendsPlan.getWorkoutPlanID(), friendsPlan);

        finish(friendsPlan.getWorkoutPlanID(), List.of(entry("0043", set(100.0, 5))));

        assertEquals("You can only log workouts from your own plans.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failureExerciseNotInPlanTest() {
        finishPushDay(entry("0025", set(62.5, 8)), entry("9999", set(20.0, 10)));

        assertEquals("Exercise 2 is no longer in this plan.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failurePlanUnreachableTest() {
        planDataAccessObject.failGetById = true;

        finishPushDay(entry("0025", set(62.5, 8)));

        assertEquals("Your workout wasn't saved. Please try again.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failureHistoryUnreachableTest() {
        workoutDataAccessObject.failGetWorkouts = true;

        finishPushDay(entry("0025", set(62.5, 8)));

        assertEquals("Your workout wasn't saved. Please try again.", presenter.error);
        assertNothingSaved();
    }

    @Test
    void failureSaveTest() {
        workoutDataAccessObject.failSave = true;

        finishPushDay(entry("0025", set(62.5, 8)));

        assertEquals("Your workout wasn't saved. Please try again.", presenter.error);
        assertNothingSaved();
    }

    /** Stands in for plan storage. */
    private static class FakePlanDataAccessObject implements FinishWorkoutPlanDataAccessInterface {
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

    /** Stands in for workout storage. Earlier workouts are added newest first. */
    private static class FakeWorkoutDataAccessObject implements FinishWorkoutDataAccessInterface {
        private final List<Workout> history = new ArrayList<>();
        private final List<Workout> saved = new ArrayList<>();
        private boolean failGetWorkouts;
        private boolean failSave;
        private UUID requestedOwnerId;

        @Override
        public List<Workout> getWorkouts(UUID ownerId) throws WorkoutStorageUnavailableException {
            if (failGetWorkouts) {
                throw new WorkoutStorageUnavailableException("offline", null);
            }
            requestedOwnerId = ownerId;
            return new ArrayList<>(history);
        }

        @Override
        public void save(Workout workout) throws WorkoutStorageUnavailableException {
            if (failSave) {
                throw new WorkoutStorageUnavailableException("offline", null);
            }
            saved.add(workout);
        }
    }

    /** Remembers which view the interactor asked for. */
    private static class RecordingPresenter implements FinishWorkoutOutputBoundary {
        private FinishWorkoutOutputData success;
        private String error;
        private int noSetsCalls;

        @Override
        public void prepareSuccessView(FinishWorkoutOutputData outputData) {
            success = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }

        @Override
        public void prepareNoSetsView() {
            noSetsCalls += 1;
        }
    }
}
