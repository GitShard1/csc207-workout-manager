package use_case.open_plan;

import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanAccessValidator;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.ArrayList;
import java.util.UUID;

public class OpenPlanInteractor implements OpenPlanInputBoundary {
    private static final String PLANS_UNREACHABLE = "Couldn't reach your plans right now. Please try again.";

    private final OpenPlanDataAccessInterface dataAccess;
    private final CurrentUserDataAccessInterface currentUserReader;
    private final OpenPlanOutputBoundary presenter;

    public OpenPlanInteractor(OpenPlanDataAccessInterface dataAccess,
                              CurrentUserDataAccessInterface currentUserReader,
                              OpenPlanOutputBoundary presenter) {
        this.dataAccess = dataAccess;
        this.currentUserReader = currentUserReader;
        this.presenter = presenter;
    }

    @Override
    public void execute(OpenPlanInputData inputData) {
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

        final WorkoutPlan targetPlan;
        try {
            targetPlan = dataAccess.getById(planId);
        }
        catch (PlanStorageUnavailableException exception) {
            presenter.prepareFailView(PLANS_UNREACHABLE);
            return;
        }

        final String accessCheck = PlanAccessValidator.checkEditable(currentUser, targetPlan);
        if (!accessCheck.isBlank()) {
            presenter.prepareFailView(accessCheck);
            return;
        }

        presenter.prepareSuccessView(toOutputData(targetPlan));
    }

    private OpenPlanOutputData toOutputData(WorkoutPlan workoutPlan) {
        final ArrayList<OpenPlanOutputData.ExerciseEntry> exerciseEntries = new ArrayList<>();
        for (PlannedExercise plannedExercise : workoutPlan.getExercises()) {
            final Exercise exercise = plannedExercise.getExercise();
            exerciseEntries.add(new OpenPlanOutputData.ExerciseEntry(exercise.getExerciseID(), exercise.getName(),
                    plannedExercise.getTargetSets(), plannedExercise.getTargetReps(),
                    plannedExercise.getTargetWeight()));
        }
        return new OpenPlanOutputData(workoutPlan.getWorkoutPlanID(), workoutPlan.getName(), exerciseEntries);
    }
}
