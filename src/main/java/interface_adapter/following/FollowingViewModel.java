package interface_adapter.following;

import interface_adapter.ViewModel;

public final class FollowingViewModel extends ViewModel<FollowingState> {
    public static final String VIEW_NAME = "Following";

    // What the user sees in the sidebar
    public static final String TITLE = "Following";

    public FollowingViewModel() {
        super(VIEW_NAME, FollowingState.empty());
    }
}
