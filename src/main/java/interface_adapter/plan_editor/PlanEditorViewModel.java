package interface_adapter.plan_editor;

import interface_adapter.ViewModel;

public final class PlanEditorViewModel extends ViewModel<PlanEditorState> {
    public static final String VIEW_NAME = "Plan Editor";

    public PlanEditorViewModel() {
        super(VIEW_NAME, PlanEditorState.empty());
    }

    /** Shared by the plan presenters: keeps the plan on screen and adds the error. */
    public void showError(String error) {
        final PlanEditorState current = getState();
        setState(new PlanEditorState(current.planId(), current.name(), current.exercises(), error));
    }
}
