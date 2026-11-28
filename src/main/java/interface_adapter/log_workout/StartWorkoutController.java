package interface_adapter.log_workout;

import use_case.start_workout.StartWorkoutInputBoundary;
import use_case.start_workout.StartWorkoutInputData;

import java.util.UUID;

public class StartWorkoutController {
    private final StartWorkoutInputBoundary interactor;

    public StartWorkoutController(StartWorkoutInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void execute(UUID planId) {
        interactor.execute(new StartWorkoutInputData(planId));
    }
}
