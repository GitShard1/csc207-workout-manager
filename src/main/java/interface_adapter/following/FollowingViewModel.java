package interface_adapter.following;

import interface_adapter.ViewModel;

public final class FollowingViewModel extends ViewModel<FollowingState> {
    public static final String VIEW_NAME = "Following";

    public FollowingViewModel() {
        super(VIEW_NAME, FollowingState.empty());
    }
}
