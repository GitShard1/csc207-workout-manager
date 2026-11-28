package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.workout_summary.WorkoutSummaryState;
import interface_adapter.workout_summary.WorkoutSummaryViewModel;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/** The View shown after a workout is saved: what was done and any new personal records. */
public final class WorkoutSummaryView extends JPanel {
    private final JLabel planLabel = new JLabel(" ");
    private final JLabel setsLabel = new JLabel(" ");
    private final DefaultListModel<String> exerciseModel = new DefaultListModel<>();
    private final JList<String> exerciseList = new JList<>(exerciseModel);
    private final DefaultListModel<String> recordModel = new DefaultListModel<>();
    private final JList<String> recordList = new JList<>(recordModel);
    private final JLabel noRecordsLabel = new JLabel("No new personal records this time.");
    private final JButton doneButton = new JButton("Done");

    public WorkoutSummaryView(WorkoutSummaryViewModel viewModel, ViewManagerModel viewManagerModel) {
        super(new BorderLayout(0, 14));
        setBorder(new EmptyBorder(16, 20, 16, 20));

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildContentPanel(), BorderLayout.CENTER);

        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(doneButton);
        add(actions, BorderLayout.SOUTH);

        doneButton.addActionListener(event -> {
            viewModel.setState(WorkoutSummaryState.empty());
            viewManagerModel.setState(HomeViewModel.VIEW_NAME);
        });

        viewModel.addPropertyChangeListener(event ->
                SwingThreads.onEdt(() -> render((WorkoutSummaryState) event.getNewValue())));
        render(viewModel.getState());
    }

    private JPanel buildHeaderPanel() {
        final JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        planLabel.setName("Summary plan");
        setsLabel.setName("Summary sets");

        header.add(left(new JLabel("Workout saved")));
        header.add(left(planLabel));
        header.add(left(setsLabel));
        return header;
    }

    private JPanel buildContentPanel() {
        final JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        exerciseList.setName("Summary exercises");
        recordList.setName("Personal records");
        noRecordsLabel.setName("No records");

        content.add(left(new JLabel("Exercises")));
        content.add(left(new JScrollPane(exerciseList)));
        content.add(left(new JLabel("New personal records")));
        content.add(left(recordList));
        content.add(left(noRecordsLabel));
        return content;
    }

    private static JComponent left(JComponent component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        return component;
    }

    private static String setCount(int totalSets) {
        if (totalSets == 1) {
            return "1 set";
        }
        return totalSets + " sets";
    }

    private void render(WorkoutSummaryState state) {
        if (state.planName().isEmpty()) {
            planLabel.setText(" ");
            setsLabel.setText(" ");
        }
        else {
            planLabel.setText(state.planName() + " - " + state.date());
            setsLabel.setText(setCount(state.totalSets()));
        }

        exerciseModel.clear();
        for (WorkoutSummaryState.ExerciseLine line : state.exercises()) {
            exerciseModel.addElement(line.exerciseName() + ": " + line.sets());
        }

        recordModel.clear();
        for (String personalRecord : state.personalRecords()) {
            recordModel.addElement(personalRecord);
        }
        recordList.setVisible(!state.personalRecords().isEmpty());
        noRecordsLabel.setVisible(state.personalRecords().isEmpty());
    }
}
