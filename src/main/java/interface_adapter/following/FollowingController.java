package interface_adapter.following;

import use_case.following.FindFollowAccountInputBoundary;
import use_case.following.FindFollowAccountInputData;
import use_case.following.FollowAccountInputBoundary;
import use_case.following.FollowAccountInputData;
import use_case.following.LoadFollowingInputBoundary;

import java.util.UUID;

public final class FollowingController {
    private final LoadFollowingInputBoundary load;
    private final FindFollowAccountInputBoundary lookup;
    private final FollowAccountInputBoundary save;

    public FollowingController(LoadFollowingInputBoundary load, FindFollowAccountInputBoundary lookup,
                               FollowAccountInputBoundary save) {
        this.load = load;
        this.lookup = lookup;
        this.save = save;
    }

    public void loadFollowing() {
        load.loadFollowing();
    }

    public void findAccount(String username) {
        lookup.findAccount(new FindFollowAccountInputData(username));
    }

    public void follow(UUID followedId) {
        save.follow(new FollowAccountInputData(followedId));
    }
}
