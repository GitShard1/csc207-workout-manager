package interface_adapter.plan_editor;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import org.junit.jupiter.api.Test;
import use_case.create_plan.CreatePlanOutputData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePlanPresenterTest {
    private final PlanEditorViewModel planEditorViewModel = new PlanEditorViewModel();
    private final ViewManagerModel viewManagerModel = new ViewManagerModel(PlanEditorViewModel.VIEW_NAME);
    private final CreatePlanPresenter presenter = new CreatePlanPresenter(planEditorViewModel, viewManagerModel);

    @Test
    void successClearsTheEditorTest() {
        planEditorViewModel.showError("Plan name is required.");
        presenter.prepareSuccessView(new CreatePlanOutputData(UUID.randomUUID(), "Push Day", 1));

        assertEquals(PlanEditorState.empty(), planEditorViewModel.getState());
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void failShowsErrorTest() {
        presenter.prepareFailView("Add at least one exercise.");

        PlanEditorState state = planEditorViewModel.getState();
        assertEquals("Add at least one exercise.", state.error());
        assertTrue(state.isNewPlan());
        assertEquals("", state.name());
        assertEquals(PlanEditorViewModel.VIEW_NAME, viewManagerModel.getState());
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
