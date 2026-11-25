package interface_adapter.edit_plan;

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
    private final EditPlanPresenter presenter = new EditPlanPresenter(planEditorViewModel);

    private final UUID planId = UUID.randomUUID();
    private final EditPlanOutputData pushDay = new EditPlanOutputData(planId, "Push Day",
            List.of(new EditPlanOutputData.ExerciseEntry("0025", "Barbell Bench Press", 3, 8, 60.0)));

    @Test
    void successShowsThePlanTest() {
        presenter.prepareSuccessView(pushDay);

        PlanEditorState state = planEditorViewModel.getState();
        assertEquals(planId, state.getPlanId());
        assertFalse(state.isNewPlan());
        assertEquals("Push Day", state.getName());
        assertEquals(List.of(new PlanEditorState.ExerciseRow("0025", "Barbell Bench Press", 3, 8, 60.0)),
                state.getExercises());
        assertNull(state.getError());
    }

    @Test
    void failShowsErrorAndKeepsThePlanTest() {
        presenter.prepareSuccessView(pushDay);
        presenter.prepareFailView("Plan name is required.");

        assertEquals("Plan name is required.", planEditorViewModel.getState().getError());
        assertEquals("Push Day", planEditorViewModel.getState().getName());
    }

    @Test
    void successAfterFailClearsTheErrorTest() {
        presenter.prepareFailView("Plan name is required.");
        presenter.prepareSuccessView(pushDay);

        assertNull(planEditorViewModel.getState().getError());
    }

    @Test
    void deleteClearsTheEditorTest() {
        presenter.prepareSuccessView(pushDay);
        presenter.prepareDeleteSuccessView();

        assertTrue(planEditorViewModel.getState().isNewPlan());
        assertEquals("", planEditorViewModel.getState().getName());
        assertTrue(planEditorViewModel.getState().getExercises().isEmpty());
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
