package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.delete_plan.DeletePlanController;
import interface_adapter.open_plan.OpenPlanController;
import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import interface_adapter.plans.ListPlansController;
import interface_adapter.plans.PlansState;
import interface_adapter.plans.PlansViewModel;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/**
 * The View for Workout Plans: lists the user's plans (List Plans).
 * New / Edit / Delete on a plan run Amgalan's plan use cases, this page just starts them.
 */
public final class PlansView extends JPanel {
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> planList = new JList<>(listModel);
    private final JLabel error = new JLabel(" ");
    private final JButton newPlan = new JButton("New plan");
    private final JButton editPlan = new JButton("Edit plan");
    private final JButton deletePlan = new JButton("Delete");

    // The plans currently listed, in the same order as the list rows (to get a row's plan id)
    private List<PlansState.PlanItem> shownPlans = List.of();

    public PlansView(PlansViewModel plansViewModel, ListPlansController listPlansController,
                     OpenPlanController openPlanController, DeletePlanController deletePlanController,
                     PlanEditorViewModel planEditorViewModel, ViewManagerModel viewManagerModel) {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(16, 20, 16, 20));

        final JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.PAGE_AXIS));
        header.add(new JLabel(PlansViewModel.TITLE));
        error.setName("Plans error");
        header.add(error);
        add(header, BorderLayout.NORTH);

        planList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(planList), BorderLayout.CENTER);

        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(newPlan);
        actions.add(editPlan);
        actions.add(deletePlan);
        add(actions, BorderLayout.SOUTH);

        // New plan: an empty editor
        newPlan.addActionListener(event -> {
            planEditorViewModel.setState(PlanEditorState.empty());
            viewManagerModel.setState(PlanEditorViewModel.VIEW_NAME);
        });

        // Edit plan: Open Plan loads it into the editor and switches there
        editPlan.addActionListener(event -> {
            final PlansState.PlanItem selected = selectedPlan();
            if (selected != null) {
                run(() -> openPlanController.execute(selected.getPlanId()));
            }
        });

        deletePlan.addActionListener(event -> {
            final PlansState.PlanItem selected = selectedPlan();
            if (selected == null) {
                return;
            }

            final int answer = JOptionPane.showConfirmDialog(this, "Delete \"" + selected.getName() + "\"?",
                    "Delete plan", JOptionPane.OK_CANCEL_OPTION);
            if (answer == JOptionPane.OK_OPTION) {
                // Then reload, so the deleted plan leaves the list
                run(() -> {
                    deletePlanController.execute(selected.getPlanId());
                    listPlansController.execute();
                });
            }
        });

        planList.addListSelectionListener(event -> updateButtons());

        // Reload the list every time the page is opened, so it's never out of date
        viewManagerModel.addPropertyChangeListener(event -> {
            if (PlansViewModel.VIEW_NAME.equals(event.getNewValue())) {
                SwingThreads.onEdt(() -> run(listPlansController::execute));
            }
        });

        plansViewModel.addPropertyChangeListener(event ->
                SwingThreads.onEdt(() -> render((PlansState) event.getNewValue())));
        render(plansViewModel.getState());
    }

    /** Runs controller calls off the Swing thread (they go over the network), with the page disabled meanwhile. */
    private void run(Runnable calls) {
        setBusy(true);

        SwingThreads.inBackground(calls,
                () -> error.setText("Something went wrong. Please try again."), () -> setBusy(false));
    }

    private void render(PlansState state) {
        shownPlans = state.getPlans();

        listModel.clear();
        for (PlansState.PlanItem plan : shownPlans) {
            // Two lines per plan without a custom renderer: name in bold, details under it
            listModel.addElement("<html><b>" + escape(plan.getName()) + "</b><br>" + plan.getDetails() + "</html>");
        }

        if (state.getError() != null) {
            error.setText(state.getError());
        } else if (shownPlans.isEmpty()) {
            error.setText("No plans yet. Make one with New plan.");
        } else {
            error.setText(" ");
        }

        updateButtons();
    }

    private PlansState.PlanItem selectedPlan() {
        final int index = planList.getSelectedIndex();
        if (index < 0 || index >= shownPlans.size()) {
            return null;
        }

        return shownPlans.get(index);
    }

    private void setBusy(boolean busy) {
        planList.setEnabled(!busy);
        newPlan.setEnabled(!busy);

        if (busy) {
            editPlan.setEnabled(false);
            deletePlan.setEnabled(false);
        } else {
            updateButtons();
        }
    }

    // Edit and Delete need a plan picked first
    private void updateButtons() {
        final boolean planPicked = selectedPlan() != null && planList.isEnabled();
        editPlan.setEnabled(planPicked);
        deletePlan.setEnabled(planPicked);
    }

    // Plan names are typed by the user, so they can't break the list's html
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
