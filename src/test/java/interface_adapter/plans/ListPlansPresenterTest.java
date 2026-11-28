package interface_adapter.plans;

import org.junit.jupiter.api.Test;
import use_case.list_plans.ListPlansOutputData;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListPlansPresenterTest {
    private final PlansViewModel viewModel = new PlansViewModel();
    private final ListPlansPresenter presenter = new ListPlansPresenter(viewModel);

    @Test
    void successListsEachPlanWithItsExerciseCountTest() {
        UUID planId = UUID.randomUUID();

        presenter.prepareSuccessView(new ListPlansOutputData(List.of(
                new ListPlansOutputData.PlanSummary(planId, "Upper body A", 6),
                new ListPlansOutputData.PlanSummary(UUID.randomUUID(), "Stretch", 1))));

        List<PlansState.PlanItem> plans = viewModel.getState().getPlans();
        assertEquals(planId, plans.get(0).getPlanId());
        assertEquals("Upper body A", plans.get(0).getName());
        assertEquals("6 exercises", plans.get(0).getDetails());
        assertEquals("1 exercise", plans.get(1).getDetails());
        assertNull(viewModel.getState().getError());
    }

    @Test
    void failKeepsTheListAndShowsTheErrorTest() {
        presenter.prepareSuccessView(new ListPlansOutputData(List.of(
                new ListPlansOutputData.PlanSummary(UUID.randomUUID(), "Leg day", 5))));

        presenter.prepareFailView("Couldn't reach your plans right now. Please try again.");

        assertEquals(1, viewModel.getState().getPlans().size());
        assertEquals("Couldn't reach your plans right now. Please try again.", viewModel.getState().getError());
    }
}
