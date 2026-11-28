package use_case.list_plans;

import entity.User;
import entity.WorkoutPlan;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The List Plans Interactor.
 * Loads the user's plans for the Workout Plans page, where edit / delete / start workout begin.
 */
public class ListPlansInteractor implements ListPlansInputBoundary {
    private final ListPlansUserDataAccessInterface userDataAccessObject;
    private final CurrentUserDataAccessInterface currentUserDataAccessObject;
    private final ListPlansOutputBoundary userPresenter;

    public ListPlansInteractor(ListPlansUserDataAccessInterface listPlansDataAccessInterface,
                               CurrentUserDataAccessInterface currentUserDataAccessObject,
                               ListPlansOutputBoundary listPlansOutputBoundary) {
        this.userDataAccessObject = listPlansDataAccessInterface;
        this.currentUserDataAccessObject = currentUserDataAccessObject;
        this.userPresenter = listPlansOutputBoundary;
    }

    @Override
    public void execute() {
        Optional<User> user = currentUserDataAccessObject.currentUser();
        if (user.isEmpty()) {
            userPresenter.prepareFailView("You need to be logged in to see your plans.");
            return;
        }

        List<WorkoutPlan> plans;
        try {
            plans = userDataAccessObject.getPlansFor(user.get().getUserID());
        } catch (PlanStorageUnavailableException exception) {
            userPresenter.prepareFailView("Couldn't reach your plans right now. Please try again.");
            return;
        }

        // Sorted by name so the list doesn't jump around between loads
        List<ListPlansOutputData.PlanSummary> summaries = new ArrayList<>();
        for (WorkoutPlan plan : plans) {
            summaries.add(new ListPlansOutputData.PlanSummary(
                    plan.getWorkoutPlanID(), plan.getName(), plan.getExercises().size()));
        }
        summaries.sort(Comparator.comparing(ListPlansOutputData.PlanSummary::getName, String.CASE_INSENSITIVE_ORDER));

        userPresenter.prepareSuccessView(new ListPlansOutputData(summaries));
    }
}
