package use_case.create_plan;

import use_case.plan_common.PlanRow;

import java.util.List;

/** The plan name is expected to be trimmed by the caller. */
public record CreatePlanInputData(String name, List<ExerciseEntry> exercises) {
    public record ExerciseEntry(String exerciseId, int sets, int reps, Double weight) implements PlanRow {
    }
}
