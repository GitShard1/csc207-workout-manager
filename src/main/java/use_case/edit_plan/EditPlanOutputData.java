package use_case.edit_plan;

import java.util.List;
import java.util.UUID;

public record EditPlanOutputData(UUID planId, String name, List<ExerciseEntry> exercises) {
    public record ExerciseEntry(String exerciseId, String exerciseName, int sets, int reps, Double weight) {
    }
}
