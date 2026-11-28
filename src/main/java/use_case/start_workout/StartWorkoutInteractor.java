package use_case.start_workout;

import entity.Exercise;
import entity.LoggedExercise;
import entity.PlannedExercise;
import entity.User;
import entity.Workout;
import entity.WorkoutPlan;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;
import use_case.workout_common.SetEntries;
import use_case.workout_common.SetEntry;
import use_case.workout_common.WorkoutPlanAccessValidator;
import use_case.workout_common.WorkoutStorageUnavailableException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StartWorkoutInteractor implements StartWorkoutInputBoundary {
    private static final String PLANS_UNREACHABLE = "Couldn't reach your plans right now. Please try again.";
    private static final String HISTORY_UNREACHABLE = "Couldn't load your past workouts right now. Please try again.";

    private final StartWorkoutPlanDataAccessInterface planDataAccess;
    private final StartWorkoutHistoryDataAccessInterface historyDataAccess;
    private final CurrentUserDataAccessInterface currentUserReader;
    private final StartWorkoutOutputBoundary presenter;

    public StartWorkoutInteractor(StartWorkoutPlanDataAccessInterface planDataAccess,
                                  StartWorkoutHistoryDataAccessInterface historyDataAccess,
                                  CurrentUserDataAccessInterface currentUserReader,
                                  StartWorkoutOutputBoundary presenter) {
        this.planDataAccess = planDataAccess;
        this.historyDataAccess = historyDataAccess;
        this.currentUserReader = currentUserReader;
        this.presenter = presenter;
    }

    @Override
    public void execute(StartWorkoutInputData inputData) {
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

        final WorkoutPlan workoutPlan;
        try {
            workoutPlan = planDataAccess.getById(planId);
        }
        catch (PlanStorageUnavailableException exception) {
            presenter.prepareFailView(PLANS_UNREACHABLE);
            return;
        }

        final String accessCheck = WorkoutPlanAccessValidator.checkStartable(currentUser, workoutPlan);
        if (!accessCheck.isBlank()) {
            presenter.prepareFailView(accessCheck);
            return;
        }

        final List<Workout> workouts;
        try {
            workouts = historyDataAccess.getWorkouts(currentUser.getUserID());
        }
        catch (WorkoutStorageUnavailableException exception) {
            presenter.prepareFailView(HISTORY_UNREACHABLE);
            return;
        }

        final ArrayList<StartWorkoutOutputData.ExerciseEntry> exerciseEntries = new ArrayList<>();
        for (PlannedExercise plannedExercise : workoutPlan.getExercises()) {
            final Exercise exercise = plannedExercise.getExercise();
            final LastTime lastTime = findLastTime(workouts, exercise.getExerciseID());
            List<SetEntry> lastSets = List.of();
            LocalDate lastDate = null;
            if (lastTime != null) {
                lastSets = lastTime.sets();
                lastDate = lastTime.date();
            }
            exerciseEntries.add(new StartWorkoutOutputData.ExerciseEntry(exercise.getExerciseID(), exercise.getName(),
                    exercise.getBodyPart().name(), exercise.getEquipment(), plannedExercise.getTargetSets(),
                    plannedExercise.getTargetReps(), plannedExercise.getTargetWeight(), lastSets, lastDate));
        }

        presenter.prepareSuccessView(new StartWorkoutOutputData(workoutPlan.getWorkoutPlanID(),
                workoutPlan.getName(), exerciseEntries));
    }

    /**
     Returns the date and completed sets of the exercise from the newest workout that has any, or null when the user
     has never done it.
     */
    private LastTime findLastTime(List<Workout> workouts, String exerciseId) {
        for (Workout workout : workouts) {
            for (LoggedExercise loggedExercise : workout.getExercises()) {
                final boolean sameExercise = loggedExercise.getExercise().getExerciseID().equals(exerciseId);
                if (sameExercise && !loggedExercise.getCompletedSets().isEmpty()) {
                    return new LastTime(workout.getDate(), SetEntries.from(loggedExercise.getCompletedSets()));
                }
            }
        }
        return null;
    }

    private record LastTime(LocalDate date, List<SetEntry> sets) {
    }
}
