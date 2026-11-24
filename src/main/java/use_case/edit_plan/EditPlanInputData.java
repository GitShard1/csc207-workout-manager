package use_case.edit_plan;

import use_case.plan_common.PlanRow;

import java.util.List;
import java.util.UUID;

/** The plan name is expected to be trimmed by the caller. */
public record EditPlanInputData(UUID planId, String name, List<ExerciseEntry> exercises) {
    public record ExerciseEntry(String exerciseId, int sets, int reps, Double weight) implements PlanRow {
    }
}
