package interface_adapter.log_workout;

import use_case.finish_workout.FinishWorkoutInputBoundary;
import use_case.finish_workout.FinishWorkoutInputData;
import use_case.workout_common.SetEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FinishWorkoutController {
    private final FinishWorkoutInputBoundary interactor;

    public FinishWorkoutController(FinishWorkoutInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void execute(UUID planId, List<String> exerciseIds, List<List<Double>> weights,
                        List<List<Integer>> reps) {
        final ArrayList<FinishWorkoutInputData.ExerciseEntry> exerciseEntries = new ArrayList<>();
        int listSize = 0;

        if (exerciseIds != null) {
            listSize = exerciseIds.size();
        }

        for (int i = 0; i < listSize; i++) {
            final ArrayList<SetEntry> setEntries = new ArrayList<>();
            final List<Double> exerciseWeights = weights.get(i);
            final List<Integer> exerciseReps = reps.get(i);
            for (int j = 0; j < exerciseWeights.size(); j++) {
                setEntries.add(new SetEntry(exerciseWeights.get(j), exerciseReps.get(j)));
            }
            exerciseEntries.add(new FinishWorkoutInputData.ExerciseEntry(exerciseIds.get(i), setEntries));
        }

        interactor.execute(new FinishWorkoutInputData(planId, exerciseEntries));
    }
}
