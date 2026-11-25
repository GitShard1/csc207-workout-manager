package interface_adapter.edit_plan;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import org.junit.jupiter.api.Test;
import use_case.edit_plan.EditPlanOutputData;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPlanPresenterTest {
    private final PlanEditorViewModel planEditorViewModel = new PlanEditorViewModel();
    private final ViewManagerModel viewManagerModel = new ViewManagerModel(HomeViewModel.VIEW_NAME);
    private final EditPlanPresenter presenter = new EditPlanPresenter(planEditorViewModel, viewManagerModel);

    private final UUID planId = UUID.randomUUID();
    private final EditPlanOutputData pushDay = new EditPlanOutputData(planId, "Push Day",
            List.of(new EditPlanOutputData.ExerciseEntry("0025", "Barbell Bench Press", 3, 8, 60.0)));

    @Test
    void successShowsThePlanTest() {
        presenter.prepareSuccessView(pushDay);

        PlanEditorState state = planEditorViewModel.getState();
        assertEquals(planId, state.planId());
        assertFalse(state.isNewPlan());
        assertEquals("Push Day", state.name());
        assertEquals(List.of(new PlanEditorState.ExerciseRow("0025", "Barbell Bench Press", 3, 8, 60.0)),
                state.exercises());
        assertNull(state.error());
        assertEquals(PlanEditorViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void failShowsErrorAndKeepsThePlanTest() {
        presenter.prepareSuccessView(pushDay);
        presenter.prepareFailView("Plan name is required.");

        assertEquals("Plan name is required.", planEditorViewModel.getState().error());
        assertEquals("Push Day", planEditorViewModel.getState().name());
        assertEquals(planId, planEditorViewModel.getState().planId());
        assertEquals(List.of(new PlanEditorState.ExerciseRow("0025", "Barbell Bench Press", 3, 8, 60.0)),
                planEditorViewModel.getState().exercises());
    }

    @Test
    void successAfterFailClearsTheErrorTest() {
        presenter.prepareFailView("Plan name is required.");
        presenter.prepareSuccessView(pushDay);

        assertNull(planEditorViewModel.getState().error());
    }

    @Test
    void deleteClearsTheEditorTest() {
        presenter.prepareSuccessView(pushDay);
        presenter.prepareDeleteSuccessView();

        assertTrue(planEditorViewModel.getState().isNewPlan());
        assertEquals("", planEditorViewModel.getState().name());
        assertTrue(planEditorViewModel.getState().exercises().isEmpty());
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void failStaysOnTheSameScreenTest() {
        viewManagerModel.setState(PlanEditorViewModel.VIEW_NAME);
        presenter.prepareFailView("Plan not found.");

        assertEquals(PlanEditorViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void viewIsToldAboutTheChangeTest() {
        int[] notifications = {0};
        planEditorViewModel.addPropertyChangeListener(event -> notifications[0]++);

        presenter.prepareSuccessView(pushDay);
        presenter.prepareFailView("Plan not found.");
        presenter.prepareDeleteSuccessView();

        assertEquals(3, notifications[0]);
    }
}
