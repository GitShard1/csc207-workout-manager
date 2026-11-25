package use_case.edit_plan;

import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.ExerciseLookupDataAccessInterface;
import use_case.plan_common.PlanInputValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EditPlanInteractor implements EditPlanInputBoundary {
    private final EditPlanDataAccessInterface dataAccess;
    private final ExerciseLookupDataAccessInterface exerciseLookup;
    private final CurrentUserDataAccessInterface currentUserReader;
    private final EditPlanOutputBoundary presenter;

    public EditPlanInteractor(EditPlanDataAccessInterface dataAccess, ExerciseLookupDataAccessInterface exerciseLookup,
                              CurrentUserDataAccessInterface currentUserReader, EditPlanOutputBoundary presenter) {
        this.dataAccess = dataAccess;
        this.exerciseLookup = exerciseLookup;
        this.currentUserReader = currentUserReader;
        this.presenter = presenter;
    }

    @Override
    public void execute(EditPlanInputData inputData) {
        User currentUser = currentUserReader.currentUser().orElse(null);
        if (currentUser == null) {
            presenter.prepareFailView("No user is logged in.");
            return;
        }

        UUID planId = inputData.planId();
        String name = inputData.name();
        List<EditPlanInputData.ExerciseEntry> exercises = inputData.exercises();

        if (planId == null) {
            presenter.prepareFailView("No plan selected.");
            return;
        }

        String inputCheck = PlanInputValidator.checkInput(name, exercises);
        if (!inputCheck.isBlank()) {
            presenter.prepareFailView(inputCheck);
            return;
        }

        WorkoutPlan targetPlan = dataAccess.getById(planId);
        if (targetPlan == null) {
            presenter.prepareFailView("Plan not found.");
            return;
        }
        else if (targetPlan.isPreset()) {
            presenter.prepareFailView("Starter plans can't be edited.");
            return;
        }
        else if (!currentUser.getUserID().equals(targetPlan.getUserOwnerID())) {
            presenter.prepareFailView("You can only edit your own plans.");
            return;
        }
        else if (!name.equalsIgnoreCase(targetPlan.getName())
                && dataAccess.existsByName(currentUser.getUserID(), name)) {
            presenter.prepareFailView("You already have a plan called \"" + name + "\".");
            return;
        }

        ArrayList<PlannedExercise> checkedExercises = new ArrayList<>();
        int row = 0;
        for (EditPlanInputData.ExerciseEntry entry : exercises) {
            row += 1;
            Exercise exercise = exerciseLookup.findById(entry.exerciseId());
            if (exercise == null) {
                presenter.prepareFailView("Row " + row + ": exercise not found.");
                return;
            }
            checkedExercises.add(new PlannedExercise(exercise, entry.sets(), entry.reps(), entry.weight()));
        }

        WorkoutPlan editedWorkoutPlan = new WorkoutPlan(targetPlan.getWorkoutPlanID(), name, currentUser.getUserID(),
                checkedExercises, targetPlan.isWorkoutCompleted(), false, targetPlan.getCopiedFromUserID());

        dataAccess.update(editedWorkoutPlan);

        EditPlanOutputData outputData = new EditPlanOutputData(editedWorkoutPlan.getWorkoutPlanID(),
                editedWorkoutPlan.getName(), editedWorkoutPlan.getExercises().size());
        presenter.prepareSuccessView(outputData);
    }
}
