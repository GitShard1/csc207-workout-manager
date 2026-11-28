package interface_adapter.plan_editor;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import org.junit.jupiter.api.Test;
import use_case.open_plan.OpenPlanOutputData;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenPlanPresenterTest {
    private final PlanEditorViewModel planEditorViewModel = new PlanEditorViewModel();
    private final ViewManagerModel viewManagerModel = new ViewManagerModel(HomeViewModel.VIEW_NAME);
    private final OpenPlanPresenter presenter = new OpenPlanPresenter(planEditorViewModel, viewManagerModel);

    private final UUID planId = UUID.randomUUID();
    private final OpenPlanOutputData pushDay = new OpenPlanOutputData(planId, "Push Day", List.of(
            new OpenPlanOutputData.ExerciseEntry("0025", "Barbell Bench Press", 3, 8, 60.0),
            new OpenPlanOutputData.ExerciseEntry("0043", "Barbell Squat", 5, 5, null)));

    @Test
    void successFillsTheEditorAndOpensItTest() {
        presenter.prepareSuccessView(pushDay);

        PlanEditorState state = planEditorViewModel.getState();
        assertEquals(planId, state.planId());
        assertFalse(state.isNewPlan());
        assertEquals("Push Day", state.name());
        assertEquals(List.of(new PlanEditorState.ExerciseRow("0025", "Barbell Bench Press", 3, 8, 60.0),
                new PlanEditorState.ExerciseRow("0043", "Barbell Squat", 5, 5, null)), state.exercises());
        assertNull(state.error());
        assertEquals(PlanEditorViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void failShowsErrorAndStaysTest() {
        presenter.prepareFailView("Starter plans can't be edited.");

        assertEquals("Starter plans can't be edited.", planEditorViewModel.getState().error());
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void viewIsToldAboutTheChangeTest() {
        int[] notifications = {0};
        planEditorViewModel.addPropertyChangeListener(event -> notifications[0]++);

        presenter.prepareSuccessView(pushDay);
        presenter.prepareFailView("Starter plans can't be edited.");

        assertEquals(2, notifications[0]);
    }
}
