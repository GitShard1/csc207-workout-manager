package use_case.create_plan;

import java.util.List;

public record CreatePlanInputData(String name, List<ExerciseEntry> exercises) {
    public record ExerciseEntry(String exerciseId, int sets, int reps, Double weight) {
    }
}
