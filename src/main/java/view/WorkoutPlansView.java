package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import interface_adapter.plan_sharing.PlanSharingController;
import use_case.plan_sharing.PlanSharingException;
import use_case.plan_sharing.PlanSharingGateway.CopyResult;
import use_case.plan_sharing.PlanSharingGateway.PlanSummary;
import use_case.plan_sharing.PlanSharingGateway.Preview;

import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Lists the signed-in user's plans and supports sharing or copying one plan. */
public final class WorkoutPlansView extends JPanel {
    public static final String VIEW_NAME = "Workout Plans";

    private static final DateTimeFormatter EXPIRATION = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault());

    private final PlanSharingController controller;
    private final BiFunction<UUID, UUID, PlanEditorState> openPlan;
    private final PlanEditorViewModel editorModel;
    private final ViewManagerModel navigation;
    private final PlanViewRequests requests;
    private final DefaultListModel<PlanSummary> plans = new DefaultListModel<>();
    private final JList<PlanSummary> planList = new JList<>(plans);
    private final JButton refresh = new JButton("Refresh plans");
    private final JButton edit = new JButton("Edit plan");
    private final JButton generate = new JButton("Generate share code");
    private final JButton revoke = new JButton("Revoke code");
    private final JButton copyCode = new JButton("Copy code");
    private final JTextField generatedCode = new JTextField(22);
    private final JLabel expiration = new JLabel(" ");
    private final JTextField incomingCode = new JTextField(22);
    private final JButton previewButton = new JButton("Preview plan");
    private final JButton saveCopy = new JButton("Save copy");
    private final JLabel previewTitle = new JLabel(" ");
    private final DefaultTableModel exerciseRows =
            new DefaultTableModel(new String[]{"Exercise", "Sets", "Reps", "Weight (kg)"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JLabel status = new JLabel(" ");
    private CopySelection copySelection;
    private boolean busy;

    public WorkoutPlansView(PlanSharingController controller, BiFunction<UUID, UUID, PlanEditorState> openPlan,
                            PlanEditorViewModel editorModel, ViewManagerModel navigation,
                            Supplier<UUID> currentUser, LongSupplier sessionGeneration) {
        super(new BorderLayout(0, 10));
        this.controller = controller;
        this.openPlan = openPlan;
        this.editorModel = editorModel;
        this.navigation = navigation;
        this.requests = new PlanViewRequests(currentUser, sessionGeneration);
        setBorder(new EmptyBorder(16, 20, 16, 20));
        add(new JLabel(VIEW_NAME), BorderLayout.NORTH);

        final JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ownedPlans(), sharedPlan());
        content.setResizeWeight(0.5);
        add(content, BorderLayout.CENTER);

        final JPanel bottom = new JPanel(new BorderLayout());
        status.setName("Workout plans status");
        bottom.add(status, BorderLayout.CENTER);
        final JButton back = new JButton("Back to Home");
        back.addActionListener(event -> navigation.setState(HomeViewModel.VIEW_NAME));
        bottom.add(back, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        connectActions();
        updateControls();
    }

    private JPanel ownedPlans() {
        final JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(0, 0, 0, 8));
        panel.add(new JLabel("Your saved plans"), BorderLayout.NORTH);
        planList.setName("Owned workout plans");
        planList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final var renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", true);
        planList.setCellRenderer((list, value, index, selected, focus) ->
                renderer.getListCellRendererComponent(list, displayPlan(value), index, selected, focus));
        panel.add(new JScrollPane(planList), BorderLayout.CENTER);

        final JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.add(row(refresh, edit));
        actions.add(row(generate, revoke));
        final JLabel codeLabel = new JLabel("Your share code");
        codeLabel.setLabelFor(generatedCode);
        generatedCode.setEditable(false);
        actions.add(row(codeLabel, generatedCode, copyCode));
        expiration.setName("Share code expiration");
        actions.add(row(expiration));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel sharedPlan() {
        final JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(0, 8, 0, 0));
        final JLabel codeLabel = new JLabel("Share code");
        codeLabel.setLabelFor(incomingCode);
        final JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(row(codeLabel, incomingCode, previewButton));
        previewTitle.setName("Shared plan name");
        previewTitle.putClientProperty("html.disable", true);
        header.add(row(previewTitle));
        panel.add(header, BorderLayout.NORTH);
        final JTable previewTable = new JTable(exerciseRows);
        previewTable.setName("Shared plan exercises");
        final var renderer = new DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", true);
        previewTable.setDefaultRenderer(Object.class, renderer);
        previewTable.setPreferredScrollableViewportSize(new Dimension(350, 160));
        panel.add(new JScrollPane(previewTable), BorderLayout.CENTER);
        panel.add(row(saveCopy), BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel row(java.awt.Component... components) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING));
        for (java.awt.Component component : components) {
            row.add(component);
        }
        return row;
    }

    private void connectActions() {
        navigation.addPropertyChangeListener(event -> SwingThreads.onEdt(() -> {
            clear();
            if (VIEW_NAME.equals(navigation.getState())) {
                loadPlans();
            }
        }));
        planList.addListSelectionListener(event -> {
            generatedCode.setText("");
            showExpiration(planList.getSelectedValue());
            updateControls();
        });
        incomingCode.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                codeChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                codeChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                codeChanged();
            }
        });
        refresh.addActionListener(event -> loadPlans());
        edit.addActionListener(event -> editSelected());
        generate.addActionListener(event -> generateCode());
        revoke.addActionListener(event -> revokeCode());
        copyCode.addActionListener(event -> copyCode());
        previewButton.addActionListener(event -> previewCode());
        saveCopy.addActionListener(event -> saveCopy());
    }

    private void clear() {
        requests.invalidate();
        busy = false;
        plans.clear();
        generatedCode.setText("");
        expiration.setText(" ");
        incomingCode.setText("");
        codeChanged();
        status.setText(" ");
    }

    private void codeChanged() {
        copySelection = null;
        previewTitle.setText(" ");
        exerciseRows.setRowCount(0);
        updateControls();
    }

    private void loadPlans() {
        run("Loading plans…", controller::list, loaded -> {
            setPlans(loaded, selectedPlan());
            status.setText(loaded.isEmpty() ? "You have no saved workout plans." : " ");
        });
    }

    private void editSelected() {
        final UUID planId = selectedPlan();
        run("Opening plan…", viewer -> openPlan.apply(viewer, planId), state -> {
            if (state.error() == null) {
                editorModel.setState(state);
                navigation.setState(PlanEditorViewModel.VIEW_NAME);
            } else {
                status.setText(state.error());
            }
        });
    }

    private void generateCode() {
        final PlanSummary selected = planList.getSelectedValue();
        generatedCode.setText("");
        run("Generating code…", viewer -> controller.generate(viewer, selected.id()), result -> {
            updateShare(selected, result.expiresAt());
            generatedCode.setText(result.code());
            status.setText("Share code generated.");
        });
    }

    private void revokeCode() {
        final PlanSummary selected = planList.getSelectedValue();
        run("Revoking code…", viewer -> {
            controller.revoke(viewer, selected.id());
            return selected;
        }, plan -> {
            updateShare(plan, null);
            generatedCode.setText("");
            status.setText("Share code revoked.");
        });
    }

    private void copyCode() {
        try {
            final StringSelection text = new StringSelection(generatedCode.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(text, null);
            status.setText("Share code copied.");
        } catch (HeadlessException | IllegalStateException | SecurityException failure) {
            generatedCode.requestFocusInWindow();
            generatedCode.selectAll();
            status.setText("Clipboard unavailable. Copy the selected code manually.");
        }
    }

    private void previewCode() {
        final String code = incomingCode.getText();
        codeChanged();
        run("Opening preview…", viewer -> controller.preview(viewer, code), preview -> {
            if (incomingCode.getText().equals(code)) {
                renderPreview(code, preview);
                status.setText(" ");
            }
        });
    }

    private void renderPreview(String code, Preview preview) {
        copySelection = new CopySelection(code, preview.revision(), UUID.randomUUID());
        previewTitle.setText(preview.name());
        for (var exercise : preview.exercises()) {
            exerciseRows.addRow(new Object[]{exercise.name(), exercise.sets(), exercise.reps(), exercise.weight()});
        }
    }

    private void saveCopy() {
        final CopySelection selection = copySelection;
        run("Saving copy…", viewer -> saveAndRefresh(viewer, selection), outcome -> {
            final CopyResult saved = outcome.saved();
            codeChanged();
            if (outcome.plans() == null) {
                plans.addElement(new PlanSummary(saved.id(), saved.name(), null));
                selectPlan(saved.id());
                status.setText("Saved " + saved.name() + ". Refresh to update your plans.");
            } else {
                setPlans(outcome.plans(), saved.id());
                status.setText("Saved " + saved.name() + ".");
            }
        });
    }

    private CopyOutcome saveAndRefresh(UUID viewer, CopySelection selection) throws PlanSharingException {
        final CopyResult saved = controller.copy(viewer, selection.code(), selection.revision(), selection.requestId());
        List<PlanSummary> refreshed;
        try {
            refreshed = controller.list(viewer);
        } catch (PlanSharingException ignored) {
            refreshed = null;
        }
        return new CopyOutcome(saved, refreshed);
    }

    private UUID selectedPlan() {
        final PlanSummary selected = planList.getSelectedValue();
        return selected == null ? null : selected.id();
    }

    private void setPlans(List<PlanSummary> refreshed, UUID selected) {
        plans.clear();
        plans.addAll(refreshed);
        selectPlan(selected);
    }

    private void selectPlan(UUID selected) {
        for (int index = 0; index < plans.size(); index++) {
            if (plans.get(index).id().equals(selected)) {
                planList.setSelectedIndex(index);
                planList.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    private void updateShare(PlanSummary plan, Instant expiresAt) {
        final PlanSummary updated = new PlanSummary(plan.id(), plan.name(), expiresAt);
        plans.set(planList.getSelectedIndex(), updated);
        showExpiration(updated);
    }

    private void showExpiration(PlanSummary selected) {
        expiration.setText(hasActiveShare(selected)
                ? "Expires " + EXPIRATION.format(selected.shareExpiresAt()) : " ");
    }

    private static boolean hasActiveShare(PlanSummary plan) {
        return plan != null && plan.shareExpiresAt() != null && plan.shareExpiresAt().isAfter(Instant.now());
    }

    private static String displayPlan(Object value) {
        if (value instanceof PlanSummary plan) {
            return plan.name();
        }
        return "";
    }

    private <T> void run(String message, Operation<T> operation, Consumer<T> success) {
        if (busy) {
            return;
        }
        final PlanViewRequests.Request request = requests.begin();
        if (request.viewer() == null) {
            status.setText("Sign in to use Workout Plans.");
            return;
        }
        busy = true;
        status.setText(message);
        updateControls();
        requests.execute(request, () -> operation.execute(request.viewer()), success, this::showFailure, () -> {
            busy = false;
            updateControls();
        });
    }

    private void showFailure(Throwable failure) {
        String message = "Workout Plans is unavailable. Please try again.";
        if (failure instanceof PlanSharingException sharingFailure) {
            message = switch (sharingFailure.reason()) {
                case SIGN_IN_REQUIRED -> "Sign in to use Workout Plans.";
                case INVALID_CODE, UNAVAILABLE, DELETED -> "This plan or code is unavailable.";
                case STALE -> "The shared plan changed. Preview it again before copying.";
                case INVALID_REQUEST -> "Check the request and try again.";
                case REQUEST_FAILED -> message;
            };
            if (sharingFailure.reason() == PlanSharingException.Reason.STALE
                    || sharingFailure.reason() == PlanSharingException.Reason.UNAVAILABLE
                    || sharingFailure.reason() == PlanSharingException.Reason.DELETED) {
                codeChanged();
            }
        }
        status.setText(message);
    }

    private void updateControls() {
        final PlanSummary selected = planList.getSelectedValue();
        planList.setEnabled(!busy);
        incomingCode.setEnabled(!busy);
        refresh.setEnabled(!busy);
        edit.setEnabled(!busy && selected != null);
        generate.setEnabled(!busy && selected != null);
        revoke.setEnabled(!busy && hasActiveShare(selected));
        copyCode.setEnabled(!busy && !generatedCode.getText().isBlank());
        previewButton.setEnabled(!busy && !incomingCode.getText().isBlank());
        saveCopy.setEnabled(!busy && copySelection != null);
    }

    @FunctionalInterface
    private interface Operation<T> {
        T execute(UUID viewer) throws PlanSharingException;
    }

    private record CopySelection(String code, String revision, UUID requestId) {
    }

    private record CopyOutcome(CopyResult saved, List<PlanSummary> plans) {
    }
}
