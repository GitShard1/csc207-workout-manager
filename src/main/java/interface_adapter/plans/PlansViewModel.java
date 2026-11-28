package interface_adapter.plans;

import interface_adapter.ViewModel;

/**
 * The View Model for the Workout Plans View.
 */
public class PlansViewModel extends ViewModel<PlansState> {

    public static final String VIEW_NAME = "Workout Plans";

    // What the user sees (heading + sidebar)
    public static final String TITLE = "Workout Plans";

    public PlansViewModel() {
        super(VIEW_NAME, PlansState.empty());
    }
}
