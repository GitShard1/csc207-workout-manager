package interface_adapter.plans;

import use_case.list_plans.ListPlansOutputBoundary;
import use_case.list_plans.ListPlansOutputData;

import java.util.ArrayList;
import java.util.List;

/**
 * The Presenter for the List Plans Use Case.
 */
public class ListPlansPresenter implements ListPlansOutputBoundary {

    private final PlansViewModel plansViewModel;

    public ListPlansPresenter(PlansViewModel plansViewModel) {
        this.plansViewModel = plansViewModel;
    }

    @Override
    public void prepareSuccessView(ListPlansOutputData outputData) {
        List<PlansState.PlanItem> items = new ArrayList<>();
        for (ListPlansOutputData.PlanSummary plan : outputData.getPlans()) {
            items.add(new PlansState.PlanItem(plan.getPlanId(), plan.getName(),
                    exerciseCountText(plan.getExerciseCount())));
        }

        plansViewModel.setState(new PlansState(items, null));
    }

    @Override
    public void prepareFailView(String errorMessage) {
        // Keep whatever was listed before, just show the error
        plansViewModel.setState(new PlansState(plansViewModel.getState().getPlans(), errorMessage));
    }

    private static String exerciseCountText(int count) {
        if (count == 1) {
            return "1 exercise";
        }

        return count + " exercises";
    }
}
