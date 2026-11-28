package interface_adapter.workout_summary;

import interface_adapter.ViewModel;

public final class WorkoutSummaryViewModel extends ViewModel<WorkoutSummaryState> {
    public static final String VIEW_NAME = "Workout Summary";

    public WorkoutSummaryViewModel() {
        super(VIEW_NAME, WorkoutSummaryState.empty());
    }
}
