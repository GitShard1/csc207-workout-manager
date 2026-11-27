package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.create_plan.CreatePlanController;
import interface_adapter.create_plan.CreatePlanPresenter;
import interface_adapter.edit_plan.EditPlanController;
import interface_adapter.edit_plan.EditPlanPresenter;
import interface_adapter.home.HomeViewModel;
import interface_adapter.open_plan.OpenPlanController;
import interface_adapter.open_plan.OpenPlanPresenter;
import interface_adapter.plan_editor.PlanEditorViewModel;
import org.junit.jupiter.api.Test;
import use_case.create_plan.CreatePlanInputData;
import use_case.create_plan.CreatePlanOutputData;
import use_case.edit_plan.EditPlanInputBoundary;
import use_case.edit_plan.EditPlanInputData;
import use_case.edit_plan.EditPlanOutputData;
import use_case.open_plan.OpenPlanOutputData;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the real plan editor view, view model and presenters, with the use cases replaced by scripted responses. */
class PlanEditorViewTest {
    private static final int NUMBER_COLUMN = 0;
    private static final int NAME_COLUMN = 1;
    private static final int SETS_COLUMN = 2;
    private static final int WEIGHT_COLUMN = 4;

    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final OpenPlanOutputData OPEN_PUSH_DAY = new OpenPlanOutputData(PLAN_ID, "Push Day", List.of(
            new OpenPlanOutputData.ExerciseEntry("0025", "Barbell Bench Press", 3, 8, 60.0),
            new OpenPlanOutputData.ExerciseEntry("0043", "Barbell Squat", 5, 5, 100.0)));

    private final ViewManagerModel viewManagerModel = new ViewManagerModel(HomeViewModel.VIEW_NAME);
    private final PlanEditorViewModel viewModel = new PlanEditorViewModel();
    private final EditPlanPresenter editPresenter = new EditPlanPresenter(viewModel, viewManagerModel);
    private final CreatePlanPresenter createPresenter = new CreatePlanPresenter(viewModel);
    private final ScriptedEditPlan editPlan = new ScriptedEditPlan(editPresenter);
    private final EditPlanController editController = new EditPlanController(editPlan);
    private final OpenPlanPresenter openPresenter = new OpenPlanPresenter(viewModel, viewManagerModel);

    private volatile UUID openedId;
    private final OpenPlanController openController = new OpenPlanController(request -> {
        openedId = request.planId();
        openPresenter.prepareSuccessView(OPEN_PUSH_DAY);
    });

    private volatile CreatePlanInputData created;
    private boolean discardConfirmed;
    private int discardAsked;
    private PlanEditorView view;

    @Test
    void loadedPlanIsShownTest() throws Exception {
        buildAndLoad();

        onEdt(() -> {
            assertTrue(hasLabel(view, "Edit plan"));
            assertEquals("Push Day", field(view, "Plan name").getText());
            assertEquals("Exercises (2)", label(view, "Exercises heading").getText());
            assertEquals(2, table(view).getModel().getRowCount());
            assertEquals(1, table(view).getModel().getValueAt(0, NUMBER_COLUMN));
            assertEquals("Barbell Bench Press", table(view).getModel().getValueAt(0, NAME_COLUMN));
            assertEquals(3, table(view).getModel().getValueAt(0, SETS_COLUMN));
            assertEquals(60.0, table(view).getModel().getValueAt(0, WEIGHT_COLUMN));
            assertEquals(2, table(view).getModel().getValueAt(1, NUMBER_COLUMN));
            assertEquals("Barbell Squat", table(view).getModel().getValueAt(1, NAME_COLUMN));
            assertEquals(5, table(view).getModel().getValueAt(1, SETS_COLUMN));
            assertEquals(100.0, table(view).getModel().getValueAt(1, WEIGHT_COLUMN));
            assertNull(findOrNull(view, JButton.class, button -> "Delete plan".equals(button.getText())));
        });
        assertEquals(PLAN_ID, openedId);
        assertEquals(PlanEditorViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void saveSendsTheEditedPlanTest() throws Exception {
        buildAndLoad();
        onEdt(() -> {
            field(view, "Plan name").setText("  Full Body ");
            table(view).getModel().setValueAt(4, 0, SETS_COLUMN);
            table(view).getModel().setValueAt(null, 0, WEIGHT_COLUMN);
            table(view).setRowSelectionInterval(1, 1);
            button(view, "Move up").doClick(0);
            button(view, "Save plan").doClick(0);
        });

        await(() -> button(view, "Save plan").isEnabled());

        assertEquals(new EditPlanInputData(PLAN_ID, "Full Body", List.of(
                new EditPlanInputData.ExerciseEntry("0043", 5, 5, 100.0),
                new EditPlanInputData.ExerciseEntry("0025", 4, 8, null))), editPlan.lastInput);
    }

    @Test
    void moveRenumbersRowsTest() throws Exception {
        buildAndLoad();
        onEdt(() -> {
            table(view).setRowSelectionInterval(1, 1);
            button(view, "Move up").doClick(0);

            assertEquals("Barbell Squat", table(view).getModel().getValueAt(0, NAME_COLUMN));
            assertEquals(1, table(view).getModel().getValueAt(0, NUMBER_COLUMN));
            assertEquals("Barbell Bench Press", table(view).getModel().getValueAt(1, NAME_COLUMN));
            assertEquals(2, table(view).getModel().getValueAt(1, NUMBER_COLUMN));
            assertEquals(0, table(view).getSelectedRow());
        });
    }

    @Test
    void removeDropsTheRowTest() throws Exception {
        buildAndLoad();
        onEdt(() -> {
            table(view).setRowSelectionInterval(0, 0);
            button(view, "Remove").doClick(0);

            assertEquals("Exercises (1)", label(view, "Exercises heading").getText());
            button(view, "Save plan").doClick(0);
        });

        await(() -> button(view, "Save plan").isEnabled());

        assertEquals(1, editPlan.lastInput.exercises().size());
        assertEquals("0043", editPlan.lastInput.exercises().get(0).exerciseId());
    }

    @Test
    void invalidCellStopsSaveTest() throws Exception {
        buildAndLoad();
        onEdt(() -> {
            table(view).editCellAt(0, SETS_COLUMN);
            ((JTextField) table(view).getEditorComponent()).setText("abc");
            button(view, "Save plan").doClick(0);
        });

        onEdt(() -> {
            assertEquals("Error: Sets and reps must be whole numbers, and weight must be a number.",
                    label(view, "Plan editor status").getText());
            assertTrue(button(view, "Save plan").isEnabled());
        });
        assertNull(editPlan.lastInput);
    }

    @Test
    void failureKeepsWhatWasTypedTest() throws Exception {
        editPlan.response = input -> editPresenter.prepareFailView("Plan name is required.");
        buildAndLoad();
        onEdt(() -> {
            field(view, "Plan name").setText("");
            button(view, "Save plan").doClick(0);
        });

        await(() -> button(view, "Save plan").isEnabled());

        onEdt(() -> {
            assertEquals("Error: Plan name is required.", label(view, "Plan editor status").getText());
            assertEquals("", field(view, "Plan name").getText());
            assertEquals(2, table(view).getModel().getRowCount());
        });
    }

    @Test
    void newPlanUsesCreateTest() throws Exception {
        build();
        onEdt(() -> {
            assertTrue(hasLabel(view, "New plan"));
            assertNull(findOrNull(view, JButton.class, button -> "Delete plan".equals(button.getText())));
            assertEquals("Exercises (0)", label(view, "Exercises heading").getText());
            field(view, "Plan name").setText("Leg Day");
            button(view, "Save plan").doClick(0);
        });

        await(() -> button(view, "Save plan").isEnabled());

        assertNotNull(created);
        assertEquals("Leg Day", created.name());
        assertTrue(created.exercises().isEmpty());
        assertNull(editPlan.lastInput);
    }

    @Test
    void cancelGoesHomeTest() throws Exception {
        build();
        viewManagerModel.setState(PlanEditorViewModel.VIEW_NAME);
        onEdt(() -> button(view, "Cancel").doClick(0));

        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void cancelBlankNewPlanDoesNotAskTest() throws Exception {
        build();
        viewManagerModel.setState(PlanEditorViewModel.VIEW_NAME);
        onEdt(() -> button(view, "Cancel").doClick(0));

        assertEquals(0, discardAsked);
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    @Test
    void cancelTypedNewPlanAsksFirstTest() throws Exception {
        build();
        discardConfirmed = false;
        viewManagerModel.setState(PlanEditorViewModel.VIEW_NAME);
        onEdt(() -> {
            field(view, "Plan name").setText("Leg Day");
            button(view, "Cancel").doClick(0);
        });

        assertEquals(1, discardAsked);
        assertEquals(PlanEditorViewModel.VIEW_NAME, viewManagerModel.getState());
        onEdt(() -> assertEquals("Leg Day", field(view, "Plan name").getText()));
    }

    @Test
    void confirmedDiscardClearsAndGoesHomeTest() throws Exception {
        build();
        discardConfirmed = true;
        viewManagerModel.setState(PlanEditorViewModel.VIEW_NAME);
        onEdt(() -> {
            field(view, "Plan name").setText("Leg Day");
            button(view, "Cancel").doClick(0);
        });

        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
        onEdt(() -> assertEquals("", field(view, "Plan name").getText()));
    }

    @Test
    void cancelWhileEditingNeverAsksTest() throws Exception {
        buildAndLoad();
        onEdt(() -> {
            field(view, "Plan name").setText("Something else");
            button(view, "Cancel").doClick(0);
        });

        assertEquals(0, discardAsked);
        assertEquals(HomeViewModel.VIEW_NAME, viewManagerModel.getState());
    }

    private void build() throws Exception {
        CreatePlanController createController = new CreatePlanController(request -> {
            created = request;
            createPresenter.prepareSuccessView(
                    new CreatePlanOutputData(UUID.randomUUID(), request.name(), request.exercises().size()));
        });
        onEdt(() -> view = new PlanEditorView(viewModel, createController, editController, viewManagerModel) {
            @Override
            boolean confirmDiscard() {
                discardAsked++;
                return discardConfirmed;
            }
        });
    }

    private void buildAndLoad() throws Exception {
        build();
        onEdt(() -> openController.execute(PLAN_ID));
    }

    /** Answers saves the way the real use case would, without a data store. */
    private static class ScriptedEditPlan implements EditPlanInputBoundary {
        private final EditPlanPresenter presenter;
        private volatile EditPlanInputData lastInput;
        private volatile Consumer<EditPlanInputData> response;

        ScriptedEditPlan(EditPlanPresenter presenter) {
            this.presenter = presenter;
            this.response = this::saveSucceeds;
        }

        @Override
        public void execute(EditPlanInputData inputData) {
            lastInput = inputData;
            response.accept(inputData);
        }

        private void saveSucceeds(EditPlanInputData inputData) {
            ArrayList<EditPlanOutputData.ExerciseEntry> entries = new ArrayList<>();
            for (EditPlanInputData.ExerciseEntry entry : inputData.exercises()) {
                entries.add(new EditPlanOutputData.ExerciseEntry(entry.exerciseId(), nameOf(entry.exerciseId()),
                        entry.sets(), entry.reps(), entry.weight()));
            }
            presenter.prepareSuccessView(new EditPlanOutputData(inputData.planId(), inputData.name(), entries));
        }

        private String nameOf(String exerciseId) {
            for (OpenPlanOutputData.ExerciseEntry known : OPEN_PUSH_DAY.exercises()) {
                if (known.exerciseId().equals(exerciseId)) {
                    return known.exerciseName();
                }
            }
            return exerciseId;
        }
    }

    private static JTextField field(Container form, String labelText) {
        return (JTextField) find(form, JLabel.class, label -> labelText.equals(label.getText())).getLabelFor();
    }

    private static JLabel label(Container container, String name) {
        return find(container, JLabel.class, label -> name.equals(label.getName()));
    }

    private static boolean hasLabel(Container container, String text) {
        return findOrNull(container, JLabel.class, label -> text.equals(label.getText())) != null;
    }

    private static JButton button(Container container, String text) {
        return find(container, JButton.class, button -> text.equals(button.getText()));
    }

    private static JTable table(Container container) {
        return find(container, JTable.class, table -> "Plan exercises".equals(table.getName()));
    }

    private static <T extends JComponent> T find(Container container, Class<T> type, Predicate<T> match) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child) && match.test(type.cast(child))) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                T found = findOrNull(nested, type, match);
                if (found != null) {
                    return found;
                }
            }
        }
        throw new AssertionError("No matching " + type.getSimpleName());
    }

    private static <T extends JComponent> T findOrNull(Container container, Class<T> type, Predicate<T> match) {
        try {
            return find(container, type, match);
        } catch (AssertionError ignored) {
            return null;
        }
    }

    private static void onEdt(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        SwingUtilities.invokeAndWait(task);
        task.get();
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            FutureTask<Boolean> check = new FutureTask<>(condition);
            SwingUtilities.invokeAndWait(check);
            if (check.get()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for the UI");
    }
}
