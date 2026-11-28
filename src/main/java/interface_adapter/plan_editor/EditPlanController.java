package interface_adapter.plan_editor;

import use_case.edit_plan.EditPlanInputBoundary;
import use_case.edit_plan.EditPlanInputData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EditPlanController {
    private final EditPlanInputBoundary interactor;

    public EditPlanController(EditPlanInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void execute(UUID planId, String name, List<String> exerciseIds, List<Integer> sets, List<Integer> reps,
                        List<Double> weights) {
        final ArrayList<EditPlanInputData.ExerciseEntry> exerciseEntries = new ArrayList<>();
        String trimmedName = null;
        int listSize = 0;

        if (name != null) {
            trimmedName = name.trim();
        }

        if (exerciseIds != null) {
            listSize = exerciseIds.size();
        }

        for (int i = 0; i < listSize; i++) {
            exerciseEntries.add(new EditPlanInputData.ExerciseEntry(exerciseIds.get(i), sets.get(i), reps.get(i),
                    weights.get(i)));
        }

        final EditPlanInputData inputData = new EditPlanInputData(planId, trimmedName, exerciseEntries);

        interactor.execute(inputData);
    }
}
