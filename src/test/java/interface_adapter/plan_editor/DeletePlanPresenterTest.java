package interface_adapter.plan_editor;

import org.junit.jupiter.api.Test;
import use_case.delete_plan.DeletePlanOutputData;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeletePlanPresenterTest {
    private final PlanEditorViewModel planEditorViewModel = new PlanEditorViewModel();
    private final DeletePlanPresenter presenter = new DeletePlanPresenter(planEditorViewModel);

    private final UUID planId = UUID.randomUUID();
    private final DeletePlanOutputData deleted = new DeletePlanOutputData(planId, "Push Day");

    private void showPushDay() {
        planEditorViewModel.setState(new PlanEditorState(planId, "Push Day",
                List.of(new PlanEditorState.ExerciseRow("0025", "Barbell Bench Press", 3, 8, 60.0)), null));
    }

    @Test
    void successClearsTheEditorTest() {
        showPushDay();

        presenter.prepareSuccessView(deleted);

        assertEquals(PlanEditorState.empty(), planEditorViewModel.getState());
    }

    @Test
    void failShowsErrorTest() {
        showPushDay();

        presenter.prepareFailView("Your plan wasn't deleted. Please try again.");

        assertEquals("Your plan wasn't deleted. Please try again.", planEditorViewModel.getState().error());
        assertEquals(planId, planEditorViewModel.getState().planId());
        assertEquals("Push Day", planEditorViewModel.getState().name());
    }

    @Test
    void viewIsToldAboutTheChangeTest() {
        int[] notifications = {0};
        planEditorViewModel.addPropertyChangeListener(event -> notifications[0]++);

        presenter.prepareSuccessView(deleted);
        presenter.prepareFailView("Your plan wasn't deleted. Please try again.");

        assertEquals(2, notifications[0]);
    }
}
