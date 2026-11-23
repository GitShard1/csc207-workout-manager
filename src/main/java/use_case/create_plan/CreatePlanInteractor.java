package use_case.create_plan;

public class CreatePlanInteractor implements CreatePlanInputBoundary {
    private final CreatePlanDataAccessInterface planDataAccess;
    private final ExerciseLookupDataAccessInterface exerciseLookup;
    private final CreatePlanOutputBoundary presenter;

    public CreatePlanInteractor(CreatePlanDataAccessInterface planDataAccess,
                                ExerciseLookupDataAccessInterface exerciseLookup,
                                CreatePlanOutputBoundary presenter) {
        this.planDataAccess = planDataAccess;
        this.exerciseLookup = exerciseLookup;
        this.presenter = presenter;
    }

    @Override
    public void execute(CreatePlanInputData inputData) {
    }
}
