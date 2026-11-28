package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.finish_workout.FinishWorkoutController;
import interface_adapter.home.HomeViewModel;
import interface_adapter.log_workout.LogWorkoutState;
import interface_adapter.log_workout.LogWorkoutViewModel;
import interface_adapter.log_workout.WorkoutFormat;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The View for a workout in progress: the user records sets, and finishes or discards the workout. */
public class LogWorkoutView extends JPanel {
    private static final int REST_SECONDS = 90;
    private static final int EXTRA_REST_SECONDS = 30;
    private static final int SET_COLUMN = 0;
    private static final int PREVIOUS_COLUMN = 1;
    private static final int WEIGHT_COLUMN = 2;
    private static final int REPS_COLUMN = 3;
    private static final int DONE_COLUMN = 4;
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final String DEFAULT_STATUS = "Completed sets are saved to your history when you finish.";
    private static final String NOT_NUMBERS = "Error: Weight and reps must be numbers.";
    private static final DateTimeFormatter STARTED_AT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final LogWorkoutViewModel viewModel;
    private final FinishWorkoutController finishWorkoutController;
    private final ViewManagerModel viewManagerModel;

    private final JLabel title = new JLabel(" ");
    private final JLabel started = new JLabel("Workout in progress");
    private final JLabel elapsedLabel = new JLabel("Elapsed 0:00");
    private final JLabel setsDoneLabel = new JLabel("Sets done 0 / 0");
    private final JLabel volumeLabel = new JLabel("Volume 0 kg");
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> exerciseList = new JList<>(listModel);
    private final JLabel positionLabel = new JLabel(" ");
    private final JLabel nameLabel = new JLabel(" ");
    private final JLabel detailsLabel = new JLabel(" ");
    private final JTable setsTable = new JTable(createModel());
    private final JButton addSetButton = new JButton("+ Add set");
    private final JButton removeSetButton = new JButton("Remove set");
    private final JButton previousButton = new JButton("Previous");
    private final JButton nextButton = new JButton("Next exercise");
    private final JPanel restPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JLabel restLabel = new JLabel(" ");
    private final JButton moreRestButton = new JButton("+30 s");
    private final JButton skipRestButton = new JButton("Skip");
    private final JLabel status = new JLabel(DEFAULT_STATUS);
    private final JButton discardButton = new JButton("Discard workout");
    private final JButton finishButton = new JButton("Finish & save");
    private final Timer timer = new Timer(1000, event -> tick());

    private final ArrayList<DefaultTableModel> models = new ArrayList<>();
    private List<LogWorkoutState.ExerciseRow> exerciseRows = List.of();
    private int elapsed;
    private int restLeft;
    private int restTotal;
    private boolean resting;
    private boolean busy;
    private boolean rebuilding;
    private boolean settingDoneBack;

    public LogWorkoutView(LogWorkoutViewModel viewModel, FinishWorkoutController finishWorkoutController,
                          ViewManagerModel viewManagerModel) {
        super(new BorderLayout(0, 14));
        this.viewModel = viewModel;
        this.finishWorkoutController = finishWorkoutController;
        this.viewManagerModel = viewManagerModel;
        setBorder(new EmptyBorder(16, 20, 16, 20));

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildContentPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        addActions();

        viewModel.addPropertyChangeListener(event ->
                SwingThreads.onEdt(() -> render((LogWorkoutState) event.getNewValue())));
        render(viewModel.getState());
        setBusy(false);
    }

    private JPanel buildHeaderPanel() {
        final JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        title.setName("Workout title");
        started.setName("Workout started");
        elapsedLabel.setName("Elapsed");
        setsDoneLabel.setName("Sets done");
        volumeLabel.setName("Volume");

        final JPanel stats = new JPanel(new GridLayout(1, 3));
        stats.add(elapsedLabel);
        stats.add(setsDoneLabel);
        stats.add(volumeLabel);

        header.add(left(title));
        header.add(left(started));
        header.add(left(stats));
        return header;
    }

    private JSplitPane buildContentPanel() {
        final JPanel exercisesPanel = new JPanel(new BorderLayout(0, 8));
        exercisesPanel.add(new JLabel("Exercises"), BorderLayout.NORTH);
        exerciseList.setName("Exercises");
        exerciseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        exercisesPanel.add(new JScrollPane(exerciseList), BorderLayout.CENTER);

        final JPanel setsPanel = new JPanel();
        setsPanel.setLayout(new BoxLayout(setsPanel, BoxLayout.Y_AXIS));
        positionLabel.setName("Exercise position");
        nameLabel.setName("Exercise name");
        detailsLabel.setName("Exercise details");
        setsTable.setName("Sets");
        setsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        final JPanel setButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        setButtons.add(addSetButton);
        setButtons.add(removeSetButton);
        setButtons.add(previousButton);
        setButtons.add(nextButton);
        setButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, setButtons.getPreferredSize().height));

        restLabel.setName("Rest time");
        restPanel.add(new JLabel("Rest"));
        restPanel.add(restLabel);
        restPanel.add(moreRestButton);
        restPanel.add(skipRestButton);
        restPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, restPanel.getPreferredSize().height));
        restPanel.setVisible(false);

        setsPanel.add(left(positionLabel));
        setsPanel.add(left(nameLabel));
        setsPanel.add(left(detailsLabel));
        setsPanel.add(left(new JScrollPane(setsTable)));
        setsPanel.add(left(setButtons));
        setsPanel.add(left(restPanel));

        return new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, exercisesPanel, setsPanel);
    }

    private JPanel buildBottomPanel() {
        final JPanel bottom = new JPanel(new BorderLayout());

        status.setName("Workout status");
        bottom.add(status, BorderLayout.CENTER);

        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(discardButton);
        actions.add(finishButton);
        bottom.add(actions, BorderLayout.EAST);

        return bottom;
    }

    private static JComponent left(JComponent component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        return component;
    }

    private void addActions() {
        exerciseList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !rebuilding) {
                showSelected();
            }
        });
        addSetButton.addActionListener(event -> addSet());
        removeSetButton.addActionListener(event -> removeSet());
        previousButton.addActionListener(event -> exerciseList.setSelectedIndex(exerciseList.getSelectedIndex() - 1));
        nextButton.addActionListener(event -> exerciseList.setSelectedIndex(exerciseList.getSelectedIndex() + 1));
        moreRestButton.addActionListener(event -> {
            restLeft += EXTRA_REST_SECONDS;
            restTotal += EXTRA_REST_SECONDS;
            showRest();
        });
        skipRestButton.addActionListener(event -> stopRest());
        finishButton.addActionListener(event -> finishWorkout());
        discardButton.addActionListener(event -> {
            if (confirmDiscard()) {
                leave();
            }
        });
    }

    private static DefaultTableModel createModel() {
        return new DefaultTableModel(new String[] {"Set", "Previous", "Weight (kg)", "Reps", "Done"}, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == SET_COLUMN || column == REPS_COLUMN) {
                    return Integer.class;
                }
                else if (column == WEIGHT_COLUMN) {
                    return Double.class;
                }
                else if (column == DONE_COLUMN) {
                    return Boolean.class;
                }
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                if (column == DONE_COLUMN) {
                    return true;
                }
                return (column == WEIGHT_COLUMN || column == REPS_COLUMN)
                        && !Boolean.TRUE.equals(getValueAt(row, DONE_COLUMN));
            }
        };
    }

    private DefaultTableModel selectedModel() {
        final int index = exerciseList.getSelectedIndex();
        if (index < 0 || index >= models.size()) {
            return null;
        }
        return models.get(index);
    }

    /** Keeps a typed value the user hasn't entered yet, unless it isn't a number. */
    private void finishCellEdit() {
        if (setsTable.isEditing() && !setsTable.getCellEditor().stopCellEditing()) {
            setsTable.getCellEditor().cancelCellEditing();
        }
    }

    private void showSelected() {
        finishCellEdit();

        final int index = exerciseList.getSelectedIndex();
        if (index < 0 || index >= models.size()) {
            setsTable.setModel(createModel());
            positionLabel.setText(" ");
            nameLabel.setText(" ");
            detailsLabel.setText(" ");
        }
        else {
            setsTable.setModel(models.get(index));
            positionLabel.setText("Exercise " + (index + 1) + " of " + models.size());
            nameLabel.setText(exerciseRows.get(index).exerciseName());
            detailsLabel.setText(exerciseRows.get(index).details());
        }
        updateNavigation();
    }

    private void updateNavigation() {
        final int index = exerciseList.getSelectedIndex();
        previousButton.setEnabled(!busy && index > 0);
        nextButton.setEnabled(!busy && index >= 0 && index < models.size() - 1);
    }

    private void onModelChanged(DefaultTableModel model, TableModelEvent event) {
        if (settingDoneBack || event.getType() != TableModelEvent.UPDATE || event.getColumn() != DONE_COLUMN) {
            return;
        }

        final int row = event.getFirstRow();
        if (!Boolean.TRUE.equals(model.getValueAt(row, DONE_COLUMN))) {
            refreshSummary();
            return;
        }

        if (model.getValueAt(row, WEIGHT_COLUMN) == null || model.getValueAt(row, REPS_COLUMN) == null) {
            untick(model, row);
            status.setText("Error: Enter a weight and reps first.");
        }
        else {
            restTotal = REST_SECONDS;
            restLeft = REST_SECONDS;
            resting = true;
            restPanel.setVisible(true);
            showRest();
            status.setText(DEFAULT_STATUS);
            refreshSummary();
        }
    }

    private void untick(DefaultTableModel model, int row) {
        settingDoneBack = true;
        model.setValueAt(false, row, DONE_COLUMN);
        settingDoneBack = false;
    }

    private void addSet() {
        final DefaultTableModel model = selectedModel();
        if (model == null) {
            return;
        }

        final int rowCount = model.getRowCount();
        Object weight = null;
        Object reps = null;
        if (rowCount > 0) {
            weight = model.getValueAt(rowCount - 1, WEIGHT_COLUMN);
            reps = model.getValueAt(rowCount - 1, REPS_COLUMN);
        }
        model.addRow(new Object[] {rowCount + 1, "", weight, reps, false});
        refreshSummary();
    }

    private void removeSet() {
        final DefaultTableModel model = selectedModel();
        final int row = setsTable.getSelectedRow();
        if (model == null || row < 0) {
            return;
        }

        if (Boolean.TRUE.equals(model.getValueAt(row, DONE_COLUMN))) {
            status.setText("Error: Untick a set before removing it.");
            return;
        }

        finishCellEdit();
        model.removeRow(row);
        for (int i = 0; i < model.getRowCount(); i++) {
            model.setValueAt(i + 1, i, SET_COLUMN);
        }
        refreshSummary();
    }

    private void refreshSummary() {
        int total = 0;
        int done = 0;
        double volume = 0;

        for (int i = 0; i < models.size(); i++) {
            final DefaultTableModel model = models.get(i);
            int doneInExercise = 0;
            for (int row = 0; row < model.getRowCount(); row++) {
                if (Boolean.TRUE.equals(model.getValueAt(row, DONE_COLUMN))) {
                    doneInExercise++;
                    final Double weight = (Double) model.getValueAt(row, WEIGHT_COLUMN);
                    final Integer reps = (Integer) model.getValueAt(row, REPS_COLUMN);
                    volume += weight * reps;
                }
            }
            total += model.getRowCount();
            done += doneInExercise;
            listModel.set(i, exerciseRows.get(i).exerciseName() + " - " + doneInExercise + " of "
                    + model.getRowCount() + " sets");
        }

        setsDoneLabel.setText("Sets done " + done + " / " + total);
        volumeLabel.setText("Volume " + WorkoutFormat.weight(volume) + " kg");
    }

    void tick() {
        elapsed++;
        elapsedLabel.setText("Elapsed " + clock(elapsed));

        if (resting) {
            restLeft--;
            if (restLeft <= 0) {
                stopRest();
            }
            else {
                showRest();
            }
        }
    }

    private static String clock(int totalSeconds) {
        final int seconds = totalSeconds % SECONDS_PER_MINUTE;
        final String secondsText = (seconds < 10 ? "0" : "") + seconds;
        if (totalSeconds >= SECONDS_PER_HOUR) {
            final int minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
            return totalSeconds / SECONDS_PER_HOUR + ":" + (minutes < 10 ? "0" : "") + minutes + ":" + secondsText;
        }
        return totalSeconds / SECONDS_PER_MINUTE + ":" + secondsText;
    }

    private void showRest() {
        restLabel.setText(clock(restLeft) + " of " + restTotal + " s");
    }

    private void stopRest() {
        resting = false;
        restPanel.setVisible(false);
    }

    private void finishWorkout() {
        if (setsTable.isEditing() && !setsTable.getCellEditor().stopCellEditing()) {
            status.setText(NOT_NUMBERS);
            return;
        }
        if (!confirmFinish()) {
            return;
        }

        final UUID planId = viewModel.getState().planId();
        final ArrayList<String> exerciseIds = new ArrayList<>();
        final ArrayList<List<Double>> weights = new ArrayList<>();
        final ArrayList<List<Integer>> reps = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            final DefaultTableModel model = models.get(i);
            final ArrayList<Double> exerciseWeights = new ArrayList<>();
            final ArrayList<Integer> exerciseReps = new ArrayList<>();
            for (int row = 0; row < model.getRowCount(); row++) {
                if (Boolean.TRUE.equals(model.getValueAt(row, DONE_COLUMN))) {
                    exerciseWeights.add((Double) model.getValueAt(row, WEIGHT_COLUMN));
                    exerciseReps.add((Integer) model.getValueAt(row, REPS_COLUMN));
                }
            }
            exerciseIds.add(exerciseRows.get(i).exerciseId());
            weights.add(exerciseWeights);
            reps.add(exerciseReps);
        }

        run("Saving workout...", () -> finishWorkoutController.execute(planId, exerciseIds, weights, reps));
    }

    private void leave() {
        timer.stop();
        stopRest();
        viewModel.setState(LogWorkoutState.empty());
        viewManagerModel.setState(HomeViewModel.VIEW_NAME);
    }

    boolean confirmFinish() {
        return JOptionPane.showConfirmDialog(this, "Finish and save this workout?", "Finish workout",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    boolean confirmDiscard() {
        return JOptionPane.showConfirmDialog(this, "Discard this workout? Nothing will be saved.",
                "Discard workout", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    boolean confirmDiscardEmpty() {
        return JOptionPane.showConfirmDialog(this, "No sets were recorded. Discard this workout?",
                "Discard workout", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    boolean isTimerRunning() {
        return timer.isRunning();
    }

    private void render(LogWorkoutState state) {
        if (state.askDiscard()) {
            if (confirmDiscardEmpty()) {
                leave();
            }
            return;
        }

        if (state.error() != null) {
            status.setText("Error: " + state.error());
            return;
        }

        rebuild(state);
    }

    private void rebuild(LogWorkoutState state) {
        title.setText(state.planName());
        started.setText("Workout in progress - started " + LocalTime.now().format(STARTED_AT));
        elapsed = 0;
        elapsedLabel.setText("Elapsed 0:00");
        stopRest();

        rebuilding = true;
        exerciseRows = state.exercises();
        models.clear();
        listModel.clear();
        for (LogWorkoutState.ExerciseRow exerciseRow : exerciseRows) {
            final DefaultTableModel model = createModel();
            int number = 1;
            for (LogWorkoutState.SetRow setRow : exerciseRow.sets()) {
                model.addRow(new Object[] {number, setRow.previous(), setRow.weight(), setRow.reps(), false});
                number++;
            }
            model.addTableModelListener(event -> onModelChanged(model, event));
            models.add(model);
            listModel.addElement(exerciseRow.exerciseName());
        }
        if (!models.isEmpty()) {
            exerciseList.setSelectedIndex(0);
        }
        rebuilding = false;

        showSelected();
        refreshSummary();
        status.setText(DEFAULT_STATUS);
        if (models.isEmpty()) {
            timer.stop();
        }
        else {
            timer.restart();
        }
    }

    private void run(String message, Runnable action) {
        status.setText(message);
        setBusy(true);
        SwingThreads.inBackground(action, () -> status.setText("Something went wrong. Please try again."),
                () -> setBusy(false));
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        setsTable.setEnabled(!busy);
        exerciseList.setEnabled(!busy);
        addSetButton.setEnabled(!busy);
        removeSetButton.setEnabled(!busy);
        moreRestButton.setEnabled(!busy);
        skipRestButton.setEnabled(!busy);
        discardButton.setEnabled(!busy);
        finishButton.setEnabled(!busy);
        updateNavigation();
    }
}
