package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.workout_summary.WorkoutSummaryState;
import interface_adapter.workout_summary.WorkoutSummaryViewModel;
import org.junit.jupiter.api.Test;

import javax.swing.JList;
import java.awt.Container;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static view.SwingTestSupport.button;
import static view.SwingTestSupport.find;
import static view.SwingTestSupport.label;
import static view.SwingTestSupport.onEdt;

/** Drives the real workout summary view and view model. */
class WorkoutSummaryViewTest {
    private final ViewManagerModel viewManagerModel = new ViewManagerModel(WorkoutSummaryViewModel.VIEW_NAME);
    private final WorkoutSummaryViewModel viewModel = new WorkoutSummaryViewModel();
    private WorkoutSummaryView view;

    private void build() throws Exception {
        onEdt(() -> view = new WorkoutSummaryView(viewModel, viewManagerModel));
    }

    private void show(WorkoutSummaryState state) throws Exception {
        onEdt(() -> viewModel.setState(state));
    }

    @Test
    void showsTheSummaryTest() throws Exception {
        build();
        show(new WorkoutSummaryState("Push Day", "Friday, November 27, 2026", 3, List.of(
                new WorkoutSummaryState.ExerciseLine("Barbell Bench Press", "60 kg x 8, 62.5 kg x 6"),
                new WorkoutSummaryState.ExerciseLine("Barbell Squat", "100 kg x 5")),
                List.of("Barbell Bench Press: 62.5 kg (previous best 60 kg)")));

        onEdt(() -> {
            assertEquals("Push Day - Friday, November 27, 2026", label(view, "Summary plan").getText());
            assertEquals("3 sets", label(view, "Summary sets").getText());
            assertEquals(2, list("Summary exercises").getModel().getSize());
            assertEquals("Barbell Bench Press: 60 kg x 8, 62.5 kg x 6",
                    list("Summary exercises").getModel().getElementAt(0));
            assertEquals("Barbell Squat: 100 kg x 5", list("Summary exercises").getModel().getElementAt(1));
            assertEquals(1, list("Personal records").getModel().getSize());
            assertEquals("Barbell Bench Press: 62.5 kg (previous best 60 kg)",
                    list("Personal records").getModel().getElementAt(0));
            assertTrue(list("Personal records").isVisible());
            assertFalse(label(view, "No records").isVisible());
        });
    }

    @Test
    void noRecordsTest() throws Exception {
        build();
        show(new WorkoutSummaryState("Push Day", "Friday, November 27, 2026", 3, List.of(
                new WorkoutSummaryState.ExerciseLine("Barbell Squat", "100 kg x 5")), List.of()));

        onEdt(() -> {
            assertTrue(label(view, "No records").isVisible());
            assertEquals("No new personal records this time.", label(view, "No records").getText());
            assertFalse(list("Personal records").isVisible());
        });
    }

    @Test
    void oneSetTest() throws Exception {
        build();
        show(new WorkoutSummaryState("Push Day", "Friday, November 27, 2026", 1, List.of(
                new WorkoutSummaryState.ExerciseLine("Barbell Squat", "100 kg x 5")), List.of()));

        onEdt(() -> assertEquals("1 set", label(view, "Summary sets").getText()));
    }

    @Test
    void doneGoesHomeTest() throws Exception {
        build();
        show(new WorkoutSummaryState("Push Day", "Friday, November 27, 2026", 1, List.of(
                new WorkoutSummaryState.ExerciseLine("Barbell Squat", "100 kg x 5")), List.of()));

        onEdt(() -> button(view, "Done").doClick(0));

        assertEquals(WorkoutSummaryState.empty(), viewModel.getState());
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
        onEdt(() -> {
            assertEquals(0, list("Summary exercises").getModel().getSize());
            assertEquals(" ", label(view, "Summary plan").getText());
            assertEquals(" ", label(view, "Summary sets").getText());
        });
    }

    private JList<?> list(String name) {
        return find((Container) view, JList.class, list -> name.equals(list.getName()));
    }
}
