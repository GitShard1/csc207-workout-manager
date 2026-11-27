package interface_adapter.create_plan;

import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import org.junit.jupiter.api.Test;
import use_case.create_plan.CreatePlanOutputData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePlanPresenterTest {
    private final PlanEditorViewModel planEditorViewModel = new PlanEditorViewModel();
    private final CreatePlanPresenter presenter = new CreatePlanPresenter(planEditorViewModel);

    @Test
    void successClearsTheEditorTest() {
        planEditorViewModel.showError("Plan name is required.");
        presenter.prepareSuccessView(new CreatePlanOutputData(UUID.randomUUID(), "Push Day", 1));

        assertEquals(PlanEditorState.empty(), planEditorViewModel.getState());
    }

    @Test
    void failShowsErrorTest() {
        presenter.prepareFailView("Add at least one exercise.");

        PlanEditorState state = planEditorViewModel.getState();
        assertEquals("Add at least one exercise.", state.error());
        assertTrue(state.isNewPlan());
        assertEquals("", state.name());
    }

    @Test
    void viewIsToldAboutTheChangeTest() {
        int[] notifications = {0};
        planEditorViewModel.addPropertyChangeListener(event -> notifications[0]++);

        presenter.prepareSuccessView(new CreatePlanOutputData(UUID.randomUUID(), "Push Day", 1));
        presenter.prepareFailView("Add at least one exercise.");

        assertEquals(2, notifications[0]);
    }
}
