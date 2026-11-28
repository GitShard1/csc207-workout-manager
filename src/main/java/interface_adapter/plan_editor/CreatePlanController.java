package interface_adapter.plan_editor;

import use_case.create_plan.CreatePlanInputBoundary;
import use_case.create_plan.CreatePlanInputData;

import java.util.ArrayList;
import java.util.List;

public class CreatePlanController {
    private final CreatePlanInputBoundary interactor;

    public CreatePlanController(CreatePlanInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void execute(String name, List<String> exerciseIds, List<Integer> sets, List<Integer> reps,
                        List<Double> weights) {
        final ArrayList<CreatePlanInputData.ExerciseEntry> exerciseEntries = new ArrayList<>();
        String trimmedName = null;
        int listSize = 0;

        if (name != null) {
            trimmedName = name.trim();
        }

        if (exerciseIds != null) {
            listSize = exerciseIds.size();
        }

        for (int i = 0; i < listSize; i++) {
            exerciseEntries.add(new CreatePlanInputData.ExerciseEntry(exerciseIds.get(i), sets.get(i), reps.get(i),
                    weights.get(i)));
        }

        final CreatePlanInputData inputData = new CreatePlanInputData(trimmedName, exerciseEntries);

        interactor.execute(inputData);
    }
}
