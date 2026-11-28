package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.log_workout.FinishWorkoutController;
import interface_adapter.log_workout.FinishWorkoutPresenter;
import interface_adapter.log_workout.LogWorkoutState;
import interface_adapter.log_workout.LogWorkoutViewModel;
import interface_adapter.log_workout.StartWorkoutPresenter;
import interface_adapter.workout_summary.WorkoutSummaryViewModel;
import org.junit.jupiter.api.Test;
import use_case.finish_workout.FinishWorkoutInputBoundary;
import use_case.finish_workout.FinishWorkoutInputData;
import use_case.finish_workout.FinishWorkoutOutputData;
import use_case.start_workout.StartWorkoutOutputData;
import use_case.workout_common.SetEntry;

import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.Container;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static view.SwingTestSupport.await;
import static view.SwingTestSupport.button;
import static view.SwingTestSupport.find;
import static view.SwingTestSupport.label;
import static view.SwingTestSupport.onEdt;

/** Drives the real workout view, view model and presenters, with the use cases replaced by scripted responses. */
class LogWorkoutViewTest {
    private static final int SET_COLUMN = 0;
    private static final int PREVIOUS_COLUMN = 1;
    private static final int WEIGHT_COLUMN = 2;
    private static final int REPS_COLUMN = 3;
    private static final int DONE_COLUMN = 4;

    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final StartWorkoutOutputData PUSH_DAY = new StartWorkoutOutputData(PLAN_ID, "Push Day", List.of(
            new StartWorkoutOutputData.ExerciseEntry("0025", "Barbell Bench Press", "CHEST", "Barbell", 3, 8, 60.0,
                    List.of(new SetEntry(60.0, 8), new SetEntry(62.5, 6)), LocalDate.of(2026, 11, 20)),
            new StartWorkoutOutputData.ExerciseEntry("0043", "Barbell Squat", "UPPER_LEGS", "Barbell", 2, 5, null,
                    List.of(), null)));

    private final ViewManagerModel viewManagerModel = new ViewManagerModel(HomeViewModel.VIEW_NAME);
    private final LogWorkoutViewModel viewModel = new LogWorkoutViewModel();
    private final WorkoutSummaryViewModel summaryViewModel = new WorkoutSummaryViewModel();
    private final StartWorkoutPresenter startPresenter = new StartWorkoutPresenter(viewModel, viewManagerModel);
    private final FinishWorkoutPresenter finishPresenter = new FinishWorkoutPresenter(viewModel, summaryViewModel,
            viewManagerModel);
    private final ScriptedFinishWorkout finishWorkout = new ScriptedFinishWorkout(finishPresenter);
    private final FinishWorkoutController finishController = new FinishWorkoutController(finishWorkout);

    private boolean finishConfirmed = true;
    private boolean discardConfirmed;
    private boolean discardEmptyConfirmed;
    private int finishAsked;
    private int discardAsked;
    private int discardEmptyAsked;
    private LogWorkoutView view;

    private void build() throws Exception {
        onEdt(() -> view = new LogWorkoutView(viewModel, finishController, viewManagerModel) {
            @Override
            boolean confirmFinish() {
                finishAsked++;
                return finishConfirmed;
            }

            @Override
            boolean confirmDiscard() {
                discardAsked++;
                return discardConfirmed;
            }

            @Override
            boolean confirmDiscardEmpty() {
                discardEmptyAsked++;
                return discardEmptyConfirmed;
            }
        });
    }

    private void buildAndLoad() throws Exception {
        build();
        onEdt(() -> startPresenter.prepareSuccessView(PUSH_DAY));
    }

    private void tickRows(int... rows) {
        for (int row : rows) {
            table().getModel().setValueAt(true, row, DONE_COLUMN);
        }
    }

    @Test
    void loadsTheWorkoutTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            assertEquals("Push Day", label(view, "Workout title").getText());
            assertTrue(label(view, "Workout started").getText().startsWith("Workout in progress - started "));
            assertEquals("Elapsed 0:00", label(view, "Elapsed").getText());
            assertEquals("Sets done 0 / 5", label(view, "Sets done").getText());
            assertEquals("Volume 0 kg", label(view, "Volume").getText());
            assertEquals("Exercise 1 of 2", label(view, "Exercise position").getText());
            assertEquals("Barbell Bench Press", label(view, "Exercise name").getText());
            assertEquals("Chest - Barbell - target 3 x 8 at 60 kg - last time Nov 20",
                    label(view, "Exercise details").getText());
            assertEquals("Barbell Bench Press - 0 of 3 sets", list("Exercises").getModel().getElementAt(0));
            assertEquals("Barbell Squat - 0 of 2 sets", list("Exercises").getModel().getElementAt(1));
            assertEquals(0, list("Exercises").getSelectedIndex());

            assertEquals(3, table().getModel().getRowCount());
            assertEquals(1, cell(0, SET_COLUMN));
            assertEquals("60 kg x 8", cell(0, PREVIOUS_COLUMN));
            assertEquals(60.0, cell(0, WEIGHT_COLUMN));
            assertEquals(8, cell(0, REPS_COLUMN));
            assertEquals(false, cell(0, DONE_COLUMN));
            assertEquals("62.5 kg x 6", cell(1, PREVIOUS_COLUMN));
            assertEquals(62.5, cell(1, WEIGHT_COLUMN));
            assertEquals(6, cell(1, REPS_COLUMN));
            assertEquals("", cell(2, PREVIOUS_COLUMN));
            assertEquals(60.0, cell(2, WEIGHT_COLUMN));
            assertEquals(8, cell(2, REPS_COLUMN));
            assertEquals("Completed sets are saved to your history when you finish.",
                    label(view, "Workout status").getText());
            assertFalse(restPanelVisible());
            assertTrue(view.isTimerRunning());
        });
        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void tickingDoneStartsRestAndUpdatesStatsTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            table().getModel().setValueAt(true, 0, DONE_COLUMN);

            assertTrue(restPanelVisible());
            assertEquals("1:30 of 90 s", label(view, "Rest time").getText());
            assertEquals("Sets done 1 / 5", label(view, "Sets done").getText());
            assertEquals("Volume 480 kg", label(view, "Volume").getText());
            assertEquals("Barbell Bench Press - 1 of 3 sets", list("Exercises").getModel().getElementAt(0));
            assertFalse(table().getModel().isCellEditable(0, WEIGHT_COLUMN));
            assertFalse(table().getModel().isCellEditable(0, REPS_COLUMN));
            assertTrue(table().getModel().isCellEditable(0, DONE_COLUMN));
            assertTrue(table().getModel().isCellEditable(1, WEIGHT_COLUMN));
        });
    }

    @Test
    void tickingWithBlankRepsIsRefusedTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            table().getModel().setValueAt(null, 0, REPS_COLUMN);
            table().getModel().setValueAt(true, 0, DONE_COLUMN);

            assertEquals(false, cell(0, DONE_COLUMN));
            assertEquals("Error: Enter a weight and reps first.", label(view, "Workout status").getText());
            assertFalse(restPanelVisible());
            assertEquals("Sets done 0 / 5", label(view, "Sets done").getText());
        });
    }

    @Test
    void untickUnlocksTheRowTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            table().getModel().setValueAt(true, 0, DONE_COLUMN);
            table().getModel().setValueAt(false, 0, DONE_COLUMN);

            assertTrue(table().getModel().isCellEditable(0, WEIGHT_COLUMN));
            assertTrue(table().getModel().isCellEditable(0, REPS_COLUMN));
            assertEquals("Sets done 0 / 5", label(view, "Sets done").getText());
            assertEquals("Volume 0 kg", label(view, "Volume").getText());
            assertEquals("Barbell Bench Press - 0 of 3 sets", list("Exercises").getModel().getElementAt(0));
        });
    }

    @Test
    void tickCountsDownAndSkipHidesRestTest() throws Exception {
        build();

        onEdt(() -> {
            startPresenter.prepareSuccessView(PUSH_DAY);
            table().getModel().setValueAt(true, 0, DONE_COLUMN);
            view.tick();
            view.tick();

            assertEquals("1:28 of 90 s", label(view, "Rest time").getText());
            assertEquals("Elapsed 0:02", label(view, "Elapsed").getText());

            button(view, "+30 s").doClick(0);
            assertEquals("1:58 of 120 s", label(view, "Rest time").getText());

            button(view, "Skip").doClick(0);
            assertFalse(restPanelVisible());
        });
    }

    @Test
    void restEndsByItselfTest() throws Exception {
        build();

        onEdt(() -> {
            startPresenter.prepareSuccessView(PUSH_DAY);
            table().getModel().setValueAt(true, 0, DONE_COLUMN);
            for (int second = 0; second < 90; second++) {
                view.tick();
            }

            assertFalse(restPanelVisible());
            assertEquals("Elapsed 1:30", label(view, "Elapsed").getText());
        });
    }

    @Test
    void addAndRemoveSetTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            button(view, "+ Add set").doClick(0);

            assertEquals(4, table().getModel().getRowCount());
            assertEquals(4, cell(3, SET_COLUMN));
            assertEquals("", cell(3, PREVIOUS_COLUMN));
            assertEquals(60.0, cell(3, WEIGHT_COLUMN));
            assertEquals(8, cell(3, REPS_COLUMN));
            assertEquals(false, cell(3, DONE_COLUMN));
            assertEquals("Barbell Bench Press - 0 of 4 sets", list("Exercises").getModel().getElementAt(0));
            assertEquals("Sets done 0 / 6", label(view, "Sets done").getText());

            table().getModel().setValueAt(true, 0, DONE_COLUMN);
            table().setRowSelectionInterval(0, 0);
            button(view, "Remove set").doClick(0);

            assertEquals(4, table().getModel().getRowCount());
            assertEquals("Error: Untick a set before removing it.", label(view, "Workout status").getText());

            table().setRowSelectionInterval(1, 1);
            button(view, "Remove set").doClick(0);

            assertEquals(3, table().getModel().getRowCount());
            assertEquals(1, cell(0, SET_COLUMN));
            assertEquals(2, cell(1, SET_COLUMN));
            assertEquals(3, cell(2, SET_COLUMN));
            assertEquals("", cell(1, PREVIOUS_COLUMN));
            assertEquals("Barbell Bench Press - 1 of 3 sets", list("Exercises").getModel().getElementAt(0));
        });
    }

    @Test
    void removeWithNothingSelectedDoesNothingTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            button(view, "Remove set").doClick(0);

            assertEquals(3, table().getModel().getRowCount());
        });
    }

    @Test
    void previousAndNextTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            assertFalse(button(view, "Previous").isEnabled());
            assertTrue(button(view, "Next exercise").isEnabled());

            button(view, "Next exercise").doClick(0);

            assertEquals("Exercise 2 of 2", label(view, "Exercise position").getText());
            assertEquals("Barbell Squat", label(view, "Exercise name").getText());
            assertEquals("Upper legs - Barbell - target 2 x 5", label(view, "Exercise details").getText());
            assertEquals(2, table().getModel().getRowCount());
            assertNull(cell(0, WEIGHT_COLUMN));
            assertEquals(5, cell(0, REPS_COLUMN));
            assertTrue(button(view, "Previous").isEnabled());
            assertFalse(button(view, "Next exercise").isEnabled());

            button(view, "Previous").doClick(0);

            assertEquals("Exercise 1 of 2", label(view, "Exercise position").getText());
            assertEquals(3, table().getModel().getRowCount());
            assertFalse(button(view, "Previous").isEnabled());
        });
    }

    @Test
    void switchingExerciseKeepsTypedValueTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            table().editCellAt(0, WEIGHT_COLUMN);
            ((JTextField) table().getEditorComponent()).setText("65");
            button(view, "Next exercise").doClick(0);
            button(view, "Previous").doClick(0);

            assertFalse(table().isEditing());
            assertEquals(65.0, cell(0, WEIGHT_COLUMN));
        });
    }

    @Test
    void switchingExerciseDropsTypedTextThatIsNotANumberTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            table().editCellAt(0, WEIGHT_COLUMN);
            ((JTextField) table().getEditorComponent()).setText("abc");
            button(view, "Next exercise").doClick(0);
            button(view, "Previous").doClick(0);

            assertFalse(table().isEditing());
            assertEquals(60.0, cell(0, WEIGHT_COLUMN));
        });
    }

    @Test
    void removeKeepsTypedValueInAnotherRowTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            table().setRowSelectionInterval(2, 2);
            table().editCellAt(0, REPS_COLUMN);
            ((JTextField) table().getEditorComponent()).setText("10");
            button(view, "Remove set").doClick(0);

            assertEquals(2, table().getModel().getRowCount());
            assertEquals(10, cell(0, REPS_COLUMN));
        });
    }

    @Test
    void finishSendsOnlyTickedSetsTest() throws Exception {
        buildAndLoad();
        onEdt(() -> {
            tickRows(0, 1);
            button(view, "Finish & save").doClick(0);
        });

        await(() -> button(view, "Finish & save").isEnabled());

        assertEquals(1, finishAsked);
        assertEquals(new FinishWorkoutInputData(PLAN_ID, List.of(
                new FinishWorkoutInputData.ExerciseEntry("0025", List.of(new SetEntry(60.0, 8),
                        new SetEntry(62.5, 6))),
                new FinishWorkoutInputData.ExerciseEntry("0043", List.of()))), finishWorkout.lastInput);
        assertEquals(LogWorkoutState.empty(), viewModel.getState());
        assertEquals(WorkoutSummaryViewModel.VIEW_NAME, viewManagerModel.getState());
        onEdt(() -> {
            assertEquals(0, table().getModel().getRowCount());
            assertFalse(view.isTimerRunning());
        });
    }

    @Test
    void finishCancelledSendsNothingTest() throws Exception {
        finishConfirmed = false;
        buildAndLoad();
        onEdt(() -> {
            tickRows(0);
            button(view, "Finish & save").doClick(0);
        });

        assertEquals(1, finishAsked);
        assertNull(finishWorkout.lastInput);
        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
        onEdt(() -> assertTrue(button(view, "Finish & save").isEnabled()));
    }

    @Test
    void finishErrorKeepsTheTablesTest() throws Exception {
        finishWorkout.response = input ->
                finishPresenter.prepareFailView("Your workout wasn't saved. Please try again.");
        buildAndLoad();
        onEdt(() -> {
            tickRows(0);
            button(view, "Finish & save").doClick(0);
        });

        await(() -> button(view, "Finish & save").isEnabled());

        onEdt(() -> {
            assertEquals("Error: Your workout wasn't saved. Please try again.",
                    label(view, "Workout status").getText());
            assertEquals(true, cell(0, DONE_COLUMN));
            assertEquals(60.0, cell(0, WEIGHT_COLUMN));
            assertEquals("Barbell Bench Press - 1 of 3 sets", list("Exercises").getModel().getElementAt(0));
        });
        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void noSetsAsksToDiscardTest() throws Exception {
        finishWorkout.response = input -> finishPresenter.prepareNoSetsView();
        discardEmptyConfirmed = true;
        buildAndLoad();
        onEdt(() -> button(view, "Finish & save").doClick(0));

        await(() -> button(view, "Finish & save").isEnabled());

        assertEquals(1, discardEmptyAsked);
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
        assertEquals(LogWorkoutState.empty(), viewModel.getState());
        onEdt(() -> assertFalse(view.isTimerRunning()));
    }

    @Test
    void noSetsDeclinedKeepsTheWorkoutTest() throws Exception {
        finishWorkout.response = input -> finishPresenter.prepareNoSetsView();
        discardEmptyConfirmed = false;
        buildAndLoad();
        onEdt(() -> button(view, "Finish & save").doClick(0));

        await(() -> button(view, "Finish & save").isEnabled());

        assertEquals(1, discardEmptyAsked);
        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
        assertEquals(3, viewModel.getState().exercises().get(0).sets().size());
        onEdt(() -> {
            assertEquals(3, table().getModel().getRowCount());
            assertTrue(view.isTimerRunning());
        });
    }

    @Test
    void discardWorkoutTest() throws Exception {
        buildAndLoad();
        onEdt(() -> button(view, "Discard workout").doClick(0));

        assertEquals(1, discardAsked);
        assertEquals(LogWorkoutViewModel.VIEW_NAME, viewManagerModel.getState());
        onEdt(() -> {
            assertEquals(3, table().getModel().getRowCount());
            assertTrue(view.isTimerRunning());
        });

        discardConfirmed = true;
        onEdt(() -> button(view, "Discard workout").doClick(0));

        assertEquals(2, discardAsked);
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
        assertEquals(LogWorkoutState.empty(), viewModel.getState());
        onEdt(() -> {
            assertEquals(0, table().getModel().getRowCount());
            assertFalse(view.isTimerRunning());
        });
    }

    private JTable table() {
        return find(view, JTable.class, table -> "Sets".equals(table.getName()));
    }

    private Object cell(int row, int column) {
        return table().getModel().getValueAt(row, column);
    }

    private JList<?> list(String name) {
        return find((Container) view, JList.class, list -> name.equals(list.getName()));
    }

    private boolean restPanelVisible() {
        return label(view, "Rest time").getParent().isVisible();
    }

    /** Answers a finished workout the way the real use case would, without a data store. */
    private static class ScriptedFinishWorkout implements FinishWorkoutInputBoundary {
        private final FinishWorkoutPresenter presenter;
        private volatile FinishWorkoutInputData lastInput;
        private volatile Consumer<FinishWorkoutInputData> response;

        ScriptedFinishWorkout(FinishWorkoutPresenter presenter) {
            this.presenter = presenter;
            this.response = this::saveSucceeds;
        }

        @Override
        public void execute(FinishWorkoutInputData inputData) {
            lastInput = inputData;
            response.accept(inputData);
        }

        private void saveSucceeds(FinishWorkoutInputData inputData) {
            presenter.prepareSuccessView(new FinishWorkoutOutputData("Push Day", LocalDate.of(2026, 11, 27),
                    List.of(), List.of()));
        }
    }
}
