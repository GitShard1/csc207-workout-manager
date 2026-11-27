package use_case.delete_plan;

import entity.User;
import entity.WorkoutPlan;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanAccessValidator;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.UUID;

public class DeletePlanInteractor implements DeletePlanInputBoundary {
    private static final String PLANS_UNREACHABLE = "Couldn't reach your plans right now. Please try again.";
    private static final String PLAN_NOT_DELETED = "Your plan wasn't deleted. Please try again.";

    private final DeletePlanDataAccessInterface dataAccess;
    private final CurrentUserDataAccessInterface currentUserReader;
    private final DeletePlanOutputBoundary presenter;

    public DeletePlanInteractor(DeletePlanDataAccessInterface dataAccess,
                                CurrentUserDataAccessInterface currentUserReader,
                                DeletePlanOutputBoundary presenter) {
        this.dataAccess = dataAccess;
        this.currentUserReader = currentUserReader;
        this.presenter = presenter;
    }

    @Override
    public void execute(DeletePlanInputData inputData) {
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

        try {
            dataAccess.delete(targetPlan.getWorkoutPlanID());
        }
        catch (PlanStorageUnavailableException exception) {
            presenter.prepareFailView(PLAN_NOT_DELETED);
            return;
        }

        presenter.prepareSuccessView(new DeletePlanOutputData(targetPlan.getWorkoutPlanID(), targetPlan.getName()));
    }
}
