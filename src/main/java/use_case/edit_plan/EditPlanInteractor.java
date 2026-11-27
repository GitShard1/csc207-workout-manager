package use_case.edit_plan;

import entity.Exercise;
import entity.PlannedExercise;
import entity.User;
import entity.WorkoutPlan;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.ExerciseLookupDataAccessInterface;
import use_case.plan_common.PlanAccessValidator;
import use_case.plan_common.PlanInputValidator;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EditPlanInteractor implements EditPlanInputBoundary {
    private static final String PLANS_UNREACHABLE = "Couldn't reach your plans right now. Please try again.";
    private static final String CHANGES_NOT_SAVED = "Your changes weren't saved. Please try again.";

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
    public void loadPlan(UUID planId) {
        final WorkoutPlan targetPlan = findEditablePlan(planId);
        if (targetPlan == null) {
            return;
        }

        presenter.prepareSuccessView(toOutputData(targetPlan));
    }

    @Override
    public void execute(EditPlanInputData inputData) {
        final String name = inputData.name();
        final List<EditPlanInputData.ExerciseEntry> exercises = inputData.exercises();

        final WorkoutPlan targetPlan = findEditablePlan(inputData.planId());
        if (targetPlan == null) {
            return;
        }

        final String inputCheck = PlanInputValidator.checkInput(name, exercises);
        if (!inputCheck.isBlank()) {
            presenter.prepareFailView(inputCheck);
            return;
        }

        final boolean nameTaken;
        try {
            nameTaken = !name.equalsIgnoreCase(targetPlan.getName())
                    && dataAccess.existsByName(targetPlan.getUserOwnerID(), name);
        }
        catch (PlanStorageUnavailableException exception) {
            presenter.prepareFailView(CHANGES_NOT_SAVED);
            return;
        }
        if (nameTaken) {
            presenter.prepareFailView("You already have a plan called \"" + name + "\".");
            return;
        }

        final ArrayList<PlannedExercise> checkedExercises = new ArrayList<>();
        int row = 0;
        for (EditPlanInputData.ExerciseEntry entry : exercises) {
            row += 1;
            final Exercise exercise = exerciseLookup.findById(entry.exerciseId());
            if (exercise == null) {
                presenter.prepareFailView("Row " + row + ": exercise not found.");
                return;
            }
            checkedExercises.add(new PlannedExercise(exercise, entry.sets(), entry.reps(), entry.weight()));
        }

        final WorkoutPlan editedWorkoutPlan = new WorkoutPlan(targetPlan.getWorkoutPlanID(), name,
                targetPlan.getUserOwnerID(), checkedExercises, targetPlan.isWorkoutCompleted(), false,
                targetPlan.getCopiedFromUserID());

        try {
            dataAccess.update(editedWorkoutPlan);
        }
        catch (PlanStorageUnavailableException exception) {
            presenter.prepareFailView(CHANGES_NOT_SAVED);
            return;
        }

        presenter.prepareSuccessView(toOutputData(editedWorkoutPlan));
    }

    @Override
    public void deletePlan(UUID planId) {
        final WorkoutPlan targetPlan = findEditablePlan(planId);
        if (targetPlan == null) {
            return;
        }

        try {
            dataAccess.delete(targetPlan.getWorkoutPlanID());
        }
        catch (PlanStorageUnavailableException exception) {
            presenter.prepareFailView("Your plan wasn't deleted. Please try again.");
            return;
        }

        presenter.prepareDeleteSuccessView();
    }

    /**
     Returns the plan if the logged-in user can change it. Otherwise reports why and returns null.
     */
    private WorkoutPlan findEditablePlan(UUID planId) {
        final User currentUser = currentUserReader.currentUser().orElse(null);
        if (currentUser == null) {
            presenter.prepareFailView("No user is logged in.");
            return null;
        }
        else if (planId == null) {
            presenter.prepareFailView("No plan selected.");
            return null;
        }

        final WorkoutPlan targetPlan;
        try {
            targetPlan = dataAccess.getById(planId);
        }
        catch (PlanStorageUnavailableException exception) {
            presenter.prepareFailView(PLANS_UNREACHABLE);
            return null;
        }
        final String accessCheck = PlanAccessValidator.checkEditable(currentUser, targetPlan);
        if (!accessCheck.isBlank()) {
            presenter.prepareFailView(accessCheck);
            return null;
        }
        return targetPlan;
    }

    private EditPlanOutputData toOutputData(WorkoutPlan workoutPlan) {
        final ArrayList<EditPlanOutputData.ExerciseEntry> exerciseEntries = new ArrayList<>();
        for (PlannedExercise plannedExercise : workoutPlan.getExercises()) {
            final Exercise exercise = plannedExercise.getExercise();
            exerciseEntries.add(new EditPlanOutputData.ExerciseEntry(exercise.getExerciseID(), exercise.getName(),
                    plannedExercise.getTargetSets(), plannedExercise.getTargetReps(),
                    plannedExercise.getTargetWeight()));
        }
        return new EditPlanOutputData(workoutPlan.getWorkoutPlanID(), workoutPlan.getName(), exerciseEntries);
    }
}
