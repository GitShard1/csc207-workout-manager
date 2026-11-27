package interface_adapter.plan_editor;

import java.util.List;
import java.util.UUID;

/** {@code planId} is null while a new plan is being created. {@code error} is null when there is nothing to report. */
public record PlanEditorState(UUID planId, String name, List<ExerciseRow> exercises, String error) {
    public PlanEditorState {
        exercises = List.copyOf(exercises);
    }

    public static PlanEditorState empty() {
        return new PlanEditorState(null, "", List.of(), null);
    }

    public boolean isNewPlan() {
        return planId == null;
    }

    public record ExerciseRow(String exerciseId, String exerciseName, int sets, int reps, Double weight) {
    }
}
