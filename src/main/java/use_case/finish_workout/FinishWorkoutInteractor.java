package use_case.finish_workout;

import entity.Exercise;
import entity.ExerciseSet;
import entity.LoggedExercise;
import entity.PlannedExercise;
import entity.User;
import entity.Workout;
import entity.WorkoutPlan;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;
import use_case.workout_common.SetEntries;
import use_case.workout_common.SetEntry;
import use_case.workout_common.SetInputValidator;
import use_case.workout_common.WorkoutPlanAccessValidator;
import use_case.workout_common.WorkoutStorageUnavailableException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FinishWorkoutInteractor implements FinishWorkoutInputBoundary {
    private static final String NOT_SAVED = "Your workout wasn't saved. Please try again.";

    private final FinishWorkoutPlanDataAccessInterface planDataAccess;
    private final FinishWorkoutDataAccessInterface dataAccess;
    private final CurrentUserDataAccessInterface currentUserReader;
    private final Clock clock;
    private final FinishWorkoutOutputBoundary presenter;

    public FinishWorkoutInteractor(FinishWorkoutPlanDataAccessInterface planDataAccess,
                                   FinishWorkoutDataAccessInterface dataAccess,
                                   CurrentUserDataAccessInterface currentUserReader, Clock clock,
                                   FinishWorkoutOutputBoundary presenter) {
        this.planDataAccess = planDataAccess;
        this.dataAccess = dataAccess;
        this.currentUserReader = currentUserReader;
        this.clock = clock;
        this.presenter = presenter;
    }

    @Override
    public void execute(FinishWorkoutInputData inputData) {
        final User currentUser = currentUserReader.currentUser().orElse(null);
        if (currentUser == null) {
            presenter.prepareFailView("No user is logged in.");
            return;
        }

        final UUID planId = inputData.planId();
        if (planId == null) {
            presenter.prepareFailView("No plan selected.");
            return;
        }

        List<FinishWorkoutInputData.ExerciseEntry> exercises = inputData.exercises();
        final int completedSets = countCompletedSets(exercises);
        if (completedSets == 0) {
            presenter.prepareNoSetsView();
            return;
        }

        final String errorMessage = checkSets(exercises);
        if (!errorMessage.isBlank()) {
            presenter.prepareFailView(errorMessage);
            return;
        }

        final WorkoutPlan workoutPlan;
        try {
            workoutPlan = planDataAccess.getById(planId);
        }
        catch (PlanStorageUnavailableException exception) {
            presenter.prepareFailView(NOT_SAVED);
            return;
        }

        final String accessCheck = WorkoutPlanAccessValidator.checkStartable(currentUser, workoutPlan);
        if (!accessCheck.isBlank()) {
            presenter.prepareFailView(accessCheck);
            return;
        }

        final ArrayList<LoggedExercise> loggedExercises = new ArrayList<>();
        for (int i = 0; i < exercises.size(); i++) {
            final FinishWorkoutInputData.ExerciseEntry exerciseEntry = exercises.get(i);
            if (exerciseEntry.completedSets().isEmpty()) {
                continue;
            }

            final Exercise exercise = findPlanExercise(workoutPlan, exerciseEntry.exerciseId());
            if (exercise == null) {
                presenter.prepareFailView("Exercise " + (i + 1) + " is no longer in this plan.");
                return;
            }

            final ArrayList<ExerciseSet> exerciseSets = new ArrayList<>();
            for (SetEntry setEntry : exerciseEntry.completedSets()) {
                exerciseSets.add(new ExerciseSet(setEntry.weight(), setEntry.reps()));
            }
            loggedExercises.add(new LoggedExercise(exercise, exerciseSets));
        }

        final List<Workout> earlierWorkouts;
        try {
            earlierWorkouts = dataAccess.getWorkouts(currentUser.getUserID());
        }
        catch (WorkoutStorageUnavailableException exception) {
            presenter.prepareFailView(NOT_SAVED);
            return;
        }

        final Workout workout = new Workout(UUID.randomUUID(), currentUser.getUserID(),
                workoutPlan.getWorkoutPlanID(), workoutPlan.getName(), LocalDate.now(clock), loggedExercises);

        try {
            dataAccess.save(workout);
        }
        catch (WorkoutStorageUnavailableException exception) {
            presenter.prepareFailView(NOT_SAVED);
            return;
        }

        final List<FinishWorkoutOutputData.PersonalRecordEntry> personalRecords =
                findPersonalRecords(workout.getExercises(), earlierWorkouts);
        presenter.prepareSuccessView(toOutputData(workout, personalRecords));
    }

    /**
     Returns the number of completed sets across every exercise.
     */
    private int countCompletedSets(List<FinishWorkoutInputData.ExerciseEntry> exercises) {
        int completed = 0;

        for (FinishWorkoutInputData.ExerciseEntry exerciseEntry : exercises) {
            completed += exerciseEntry.completedSets().size();
        }

        return completed;
    }

    /**
     Checks every completed set, in order. Returns the first problem as "Exercise n, set m: problem", counting
     both from 1, or empty string when every set is correct.
     */
    private String checkSets(List<FinishWorkoutInputData.ExerciseEntry> exercises) {
        for (int i = 0; i < exercises.size(); i++) {
            final List<SetEntry> sets = exercises.get(i).completedSets();
            for (int j = 0; j < sets.size(); j++) {
                SetEntry currentSet = sets.get(j);
                String errorMessage = SetInputValidator.checkSet(currentSet.weight(), currentSet.reps());
                if (!errorMessage.isBlank()) {
                    return "Exercise " + (i + 1) + ", set " + (j + 1) + ": " + errorMessage;
                }
            }
        }
        return "";
    }

    /**
     Returns the plan's exercise with this ID, or null when the plan doesn't have it.
     */
    private Exercise findPlanExercise(WorkoutPlan plan, String exerciseId) {
        for (PlannedExercise plannedExercise : plan.getExercises()) {
            final Exercise exercise = plannedExercise.getExercise();
            if (exercise.getExerciseID().equals(exerciseId)) {
                return exercise;
            }
        }
        return null;
    }

    /**
     Returns one record for each exercise whose heaviest weight in this workout beats its heaviest weight in every
     earlier workout. Exercises the user has never done before are not records.
     */
    private List<FinishWorkoutOutputData.PersonalRecordEntry> findPersonalRecords(
            List<LoggedExercise> loggedExercises, List<Workout> earlierWorkouts) {
        final ArrayList<FinishWorkoutOutputData.PersonalRecordEntry> personalRecords = new ArrayList<>();
        final ArrayList<String> handledExerciseIds = new ArrayList<>();

        for (LoggedExercise loggedExercise : loggedExercises) {
            final String exerciseId = loggedExercise.getExercise().getExerciseID();
            if (handledExerciseIds.contains(exerciseId)) {
                continue;
            }
            handledExerciseIds.add(exerciseId);

            final double previousBest = heaviestBefore(earlierWorkouts, exerciseId);
            if (previousBest < 0) {
                continue;
            }

            final double heaviest = heaviestIn(loggedExercises, exerciseId);
            if (heaviest > previousBest) {
                personalRecords.add(new FinishWorkoutOutputData.PersonalRecordEntry(
                        loggedExercise.getExercise().getName(), heaviest, previousBest));
            }
        }

        return personalRecords;
    }

    /**
     Returns the heaviest weight across the sets of every entry with this exercise ID, or -1 when there are none.
     */
    private double heaviestIn(List<LoggedExercise> loggedExercises, String exerciseId) {
        double heaviest = -1;

        for (LoggedExercise loggedExercise : loggedExercises) {
            if (!loggedExercise.getExercise().getExerciseID().equals(exerciseId)) {
                continue;
            }
            for (ExerciseSet exerciseSet : loggedExercise.getCompletedSets()) {
                heaviest = Math.max(heaviest, exerciseSet.getWeight());
            }
        }

        return heaviest;
    }

    /**
     Returns the heaviest weight lifted for this exercise ID in any of the workouts, or -1 when there is none.
     */
    private double heaviestBefore(List<Workout> workouts, String exerciseId) {
        double heaviest = -1;

        for (Workout workout : workouts) {
            heaviest = Math.max(heaviest, heaviestIn(workout.getExercises(), exerciseId));
        }

        return heaviest;
    }

    private FinishWorkoutOutputData toOutputData(Workout workout,
                                                 List<FinishWorkoutOutputData.PersonalRecordEntry> personalRecords) {
        final ArrayList<FinishWorkoutOutputData.ExerciseSummary> exerciseSummaries = new ArrayList<>();
        for (LoggedExercise loggedExercise : workout.getExercises()) {
            exerciseSummaries.add(new FinishWorkoutOutputData.ExerciseSummary(loggedExercise.getExercise().getName(),
                    SetEntries.from(loggedExercise.getCompletedSets())));
        }
        return new FinishWorkoutOutputData(workout.getPlanName(), workout.getDate(), exerciseSummaries,
                personalRecords);
    }
}
