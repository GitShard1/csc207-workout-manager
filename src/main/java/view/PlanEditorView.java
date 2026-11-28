package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.create_plan.CreatePlanController;
import interface_adapter.edit_plan.EditPlanController;
import interface_adapter.home.HomeViewModel;
import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** The View for the plan editor. Used to create a new plan, or to change an existing one. */
public class PlanEditorView extends JPanel {
    private static final int NUMBER_COLUMN = 0;
    private static final int SETS_COLUMN = 2;
    private static final int REPS_COLUMN = 3;
    private static final int WEIGHT_COLUMN = 4;
    private static final int ID_COLUMN = 5;

    private final PlanEditorViewModel viewModel;
    private final CreatePlanController createPlanController;
    private final EditPlanController editPlanController;
    private final ViewManagerModel viewManagerModel;
    private final EditOwnedPlan editOwnedPlan;
    private final PlanViewRequests ownedRequests;

    private final JLabel title = new JLabel("New plan");
    private final JTextField nameField = new JTextField(24);
    private final JLabel exercisesHeading = new JLabel("Exercises (0)");
    private final DefaultTableModel exerciseTableModel = new DefaultTableModel(
            new String[] {"#", "Exercise", "Sets", "Reps", "Weight (kg)", "Exercise ID"}, 0) {
        @Override
        public Class<?> getColumnClass(int column) {
            if (column == NUMBER_COLUMN || column == SETS_COLUMN || column == REPS_COLUMN) {
                return Integer.class;
            }
            else if (column == WEIGHT_COLUMN) {
                return Double.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column >= SETS_COLUMN && column <= WEIGHT_COLUMN;
        }
    };
    private final JTable exerciseTable = new JTable(exerciseTableModel);
    private final JButton removeButton = new JButton("Remove");
    private final JButton moveUpButton = new JButton("Move up");
    private final JButton moveDownButton = new JButton("Move down");
    private final JLabel status = new JLabel(" ");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton saveButton = new JButton("Save plan");

    public PlanEditorView(PlanEditorViewModel viewModel, CreatePlanController createPlanController,
                          EditPlanController editPlanController, ViewManagerModel viewManagerModel) {
        this(viewModel, createPlanController, editPlanController, viewManagerModel, null, null);
    }

    /** Opens the existing editor for already saved plans and scopes its saves to the current sign-in. */
    public PlanEditorView(PlanEditorViewModel viewModel, ViewManagerModel viewManagerModel, EditOwnedPlan editOwnedPlan,
                          Supplier<UUID> currentUser, LongSupplier sessionGeneration) {
        this(viewModel, null, null, viewManagerModel, editOwnedPlan,
                new PlanViewRequests(currentUser, sessionGeneration));
        cancelButton.setText("Back to Workout Plans");
        viewManagerModel.addPropertyChangeListener(event -> SwingThreads.onEdt(() -> {
            if (!PlanEditorViewModel.VIEW_NAME.equals(event.getNewValue())) {
                ownedRequests.invalidate();
                viewModel.setState(PlanEditorState.empty());
                setBusy(false);
            }
        }));
    }

    private PlanEditorView(PlanEditorViewModel viewModel, CreatePlanController createPlanController,
                           EditPlanController editPlanController, ViewManagerModel viewManagerModel,
                           EditOwnedPlan editOwnedPlan, PlanViewRequests ownedRequests) {
        super(new BorderLayout(0, 14));
        this.viewModel = viewModel;
        this.createPlanController = createPlanController;
        this.editPlanController = editPlanController;
        this.viewManagerModel = viewManagerModel;
        this.editOwnedPlan = editOwnedPlan;
        this.ownedRequests = ownedRequests;
        setBorder(new EmptyBorder(16, 20, 16, 20));

        add(title, BorderLayout.NORTH);

        final JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(buildDetailsPanel());
        content.add(buildExercisesPanel());
        add(content, BorderLayout.CENTER);

        add(buildBottomPanel(), BorderLayout.SOUTH);

        addActions();

        viewModel.addPropertyChangeListener(event ->
                SwingThreads.onEdt(() -> render((PlanEditorState) event.getNewValue())));
        render(viewModel.getState());
        setBusy(false);
    }

    private JPanel buildDetailsPanel() {
        final JPanel details = new JPanel(new GridBagLayout());
        details.setAlignmentX(LEFT_ALIGNMENT);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 8, 8);

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        details.add(new JLabel("Plan details"), constraints);

        final JLabel nameLabel = new JLabel("Plan name");
        nameLabel.setLabelFor(nameField);
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        details.add(nameLabel, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        details.add(nameField, constraints);

        details.setMaximumSize(new Dimension(Integer.MAX_VALUE, details.getPreferredSize().height));
        return details;
    }

    private JPanel buildExercisesPanel() {
        final JPanel exercises = new JPanel(new BorderLayout(8, 8));
        exercises.setAlignmentX(LEFT_ALIGNMENT);

        exercisesHeading.setName("Exercises heading");
        exercises.add(exercisesHeading, BorderLayout.NORTH);

        exerciseTable.setName("Plan exercises");
        exerciseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final var renderer = new DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", true);
        exerciseTable.setDefaultRenderer(String.class, renderer);
        exerciseTable.removeColumn(exerciseTable.getColumnModel().getColumn(ID_COLUMN));
        exercises.add(new JScrollPane(exerciseTable), BorderLayout.CENTER);

        final JPanel rowButtons = new JPanel(new GridLayout(0, 1, 0, 8));
        rowButtons.add(removeButton);
        rowButtons.add(moveUpButton);
        rowButtons.add(moveDownButton);
        final JPanel rowButtonsWrapper = new JPanel(new BorderLayout());
        rowButtonsWrapper.add(rowButtons, BorderLayout.NORTH);
        exercises.add(rowButtonsWrapper, BorderLayout.EAST);

        return exercises;
    }

    private JPanel buildBottomPanel() {
        final JPanel bottom = new JPanel(new BorderLayout());

        status.setName("Plan editor status");
        bottom.add(status, BorderLayout.CENTER);

        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancelButton);
        actions.add(saveButton);
        bottom.add(actions, BorderLayout.EAST);

        return bottom;
    }

    private void addActions() {
        removeButton.addActionListener(event -> {
            final int row = exerciseTable.getSelectedRow();
            if (row >= 0) {
                exerciseTableModel.removeRow(row);
                renumberRows();
            }
        });
        moveUpButton.addActionListener(event -> {
            final int row = exerciseTable.getSelectedRow();
            if (row > 0) {
                exerciseTableModel.moveRow(row, row, row - 1);
                renumberRows();
                exerciseTable.setRowSelectionInterval(row - 1, row - 1);
            }
        });
        moveDownButton.addActionListener(event -> {
            final int row = exerciseTable.getSelectedRow();
            if (row >= 0 && row < exerciseTableModel.getRowCount() - 1) {
                exerciseTableModel.moveRow(row, row, row + 1);
                renumberRows();
                exerciseTable.setRowSelectionInterval(row + 1, row + 1);
            }
        });
        saveButton.addActionListener(event -> savePlan());
        cancelButton.addActionListener(event -> cancel());
    }

    private void savePlan() {
        if (exerciseTable.isEditing() && !exerciseTable.getCellEditor().stopCellEditing()) {
            status.setText("Error: Sets and reps must be whole numbers, and weight must be a number.");
            return;
        }

        final String name = nameField.getText();
        final ArrayList<String> exerciseIds = new ArrayList<>();
        final ArrayList<Integer> sets = new ArrayList<>();
        final ArrayList<Integer> reps = new ArrayList<>();
        final ArrayList<Double> weights = new ArrayList<>();
        for (int row = 0; row < exerciseTableModel.getRowCount(); row++) {
            exerciseIds.add((String) exerciseTableModel.getValueAt(row, ID_COLUMN));
            sets.add(countAt(row, SETS_COLUMN));
            reps.add(countAt(row, REPS_COLUMN));
            weights.add((Double) exerciseTableModel.getValueAt(row, WEIGHT_COLUMN));
        }

        final PlanEditorState state = viewModel.getState();
        if (editOwnedPlan != null) {
            saveOwnedPlan(state.planId(), name, exerciseIds, sets, reps, weights);
        }
        else if (state.isNewPlan()) {
            run("Saving plan...", () -> createPlanController.execute(name, exerciseIds, sets, reps, weights));
        }
        else {
            final UUID planId = state.planId();
            run("Saving plan...", () -> editPlanController.execute(planId, name, exerciseIds, sets, reps, weights));
        }
    }

    private void saveOwnedPlan(UUID planId, String name, List<String> exerciseIds, List<Integer> sets,
                               List<Integer> reps, List<Double> weights) {
        final PlanViewRequests.Request request = ownedRequests.begin();
        if (request.viewer() == null || planId == null) {
            status.setText("This plan is unavailable.");
            return;
        }
        status.setText("Saving plan...");
        setBusy(true);
        ownedRequests.execute(request,
                () -> editOwnedPlan.save(request.viewer(), planId, name, exerciseIds, sets, reps, weights),
                state -> {
                    if (state.error() == null) {
                        viewModel.setState(state);
                        status.setText("Plan saved.");
                    } else {
                        status.setText("Error: " + state.error());
                    }
                }, failure -> status.setText("Something went wrong. Please try again."), () -> setBusy(false));
    }

    private Integer countAt(int row, int column) {
        final Integer value = (Integer) exerciseTableModel.getValueAt(row, column);
        if (value == null) {
            return 0;
        }
        return value;
    }

    private void cancel() {
        if (editOwnedPlan != null) {
            viewManagerModel.setState(WorkoutPlansView.VIEW_NAME);
            return;
        }
        final boolean typedSomething = !nameField.getText().isBlank() || exerciseTableModel.getRowCount() > 0;
        if (viewModel.getState().isNewPlan() && typedSomething && !confirmDiscard()) {
            return;
        }
        viewModel.setState(PlanEditorState.empty());
        viewManagerModel.setState(HomeViewModel.VIEW_NAME);
    }

    boolean confirmDiscard() {
        return JOptionPane.showConfirmDialog(this, "Discard this plan? It hasn't been saved.", "Discard plan",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void render(PlanEditorState state) {
        title.setText(state.isNewPlan() ? "New plan" : "Edit plan");

        if (state.error() != null) {
            status.setText("Error: " + state.error());
            return;
        }

        status.setText(" ");
        nameField.setText(state.name());
        exerciseTableModel.setRowCount(0);
        for (PlanEditorState.ExerciseRow row : state.exercises()) {
            exerciseTableModel.addRow(new Object[] {0, row.exerciseName(), row.sets(), row.reps(), row.weight(),
                    row.exerciseId()});
        }
        renumberRows();
    }

    private void renumberRows() {
        final int rowCount = exerciseTableModel.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            exerciseTableModel.setValueAt(row + 1, row, NUMBER_COLUMN);
        }
        exercisesHeading.setText("Exercises (" + rowCount + ")");
    }

    private void run(String message, Runnable action) {
        status.setText(message);
        setBusy(true);
        SwingThreads.inBackground(action, () -> status.setText("Something went wrong. Please try again."),
                () -> setBusy(false));
    }

    private void setBusy(boolean busy) {
        nameField.setEnabled(!busy);
        exerciseTable.setEnabled(!busy);
        removeButton.setEnabled(!busy);
        moveUpButton.setEnabled(!busy);
        moveDownButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy || editOwnedPlan != null);
        saveButton.setEnabled(!busy);
    }

    @FunctionalInterface
    public interface EditOwnedPlan {
        PlanEditorState save(UUID viewer, UUID planId, String name, List<String> exerciseIds,
                             List<Integer> sets, List<Integer> reps, List<Double> weights);
    }
}
