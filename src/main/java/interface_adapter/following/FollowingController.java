package interface_adapter.following;

import use_case.account.AccountProfile;
import use_case.following.FollowingInputBoundary;

public final class FollowingController {
    private final FollowingInputBoundary following;

    public FollowingController(FollowingInputBoundary following) {
        this.following = following;
    }

    public void loadFollowing() {
        following.loadFollowing();
    }

    public void findAccount(String username) {
        following.findAccount(username);
    }

    public void follow(AccountProfile account) {
        following.follow(account);
    }
}
