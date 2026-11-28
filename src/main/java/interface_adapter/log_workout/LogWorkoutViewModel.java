package interface_adapter.log_workout;

import interface_adapter.ViewModel;

public final class LogWorkoutViewModel extends ViewModel<LogWorkoutState> {
    public static final String VIEW_NAME = "Log Workout";

    public LogWorkoutViewModel() {
        super(VIEW_NAME, LogWorkoutState.empty());
    }

    /** Shared by the workout presenters: keeps the workout on screen and adds the error. */
    public void showError(String error) {
        setState(getState().withError(error));
    }
}
