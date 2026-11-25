package use_case.create_plan;

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

public class CreatePlanInteractor implements CreatePlanInputBoundary {
    private final CreatePlanDataAccessInterface planDataAccess;
    private final ExerciseLookupDataAccessInterface exerciseLookup;
    private final CurrentUserDataAccessInterface currentUserReader;
    private final CreatePlanOutputBoundary presenter;

    public CreatePlanInteractor(CreatePlanDataAccessInterface planDataAccess,
                                ExerciseLookupDataAccessInterface exerciseLookup,
                                CurrentUserDataAccessInterface currentUserReader,
                                CreatePlanOutputBoundary presenter) {
        this.planDataAccess = planDataAccess;
        this.exerciseLookup = exerciseLookup;
        this.currentUserReader = currentUserReader;
        this.presenter = presenter;
    }

    @Override
    public void execute(CreatePlanInputData inputData) {
        final User currentUser = currentUserReader.currentUser().orElse(null);
        if (currentUser == null) {
            presenter.prepareFailView("No user is logged in.");
            return;
        }

        final UUID ownerId = currentUser.getUserID();
        final String name = inputData.name();
        final List<CreatePlanInputData.ExerciseEntry> exercises = inputData.exercises();

        final String inputCheck = PlanInputValidator.checkInput(name, exercises);
        if (!inputCheck.isBlank()) {
            presenter.prepareFailView(inputCheck);
            return;
        }

        if (planDataAccess.existsByName(ownerId, name)) {
            presenter.prepareFailView("You already have a plan called \"" + name + "\".");
            return;
        }

        final ArrayList<PlannedExercise> templateExercises = new ArrayList<>();
        int row = 0;
        for (CreatePlanInputData.ExerciseEntry entry : exercises) {
            row += 1;
            final Exercise exercise = exerciseLookup.findById(entry.exerciseId());
            if (exercise == null) {
                presenter.prepareFailView("Row " + row + ": exercise not found.");
                return;
            }
            templateExercises.add(new PlannedExercise(exercise, entry.sets(), entry.reps(), entry.weight()));
        }

        final WorkoutPlan newPlan = new WorkoutPlan(UUID.randomUUID(), name, ownerId,
                templateExercises, false, false, null);

        planDataAccess.save(newPlan);

        final CreatePlanOutputData outputData = new CreatePlanOutputData(newPlan.getWorkoutPlanID(), newPlan.getName(),
                newPlan.getExercises().size());
        presenter.prepareSuccessView(outputData);
    }
}
