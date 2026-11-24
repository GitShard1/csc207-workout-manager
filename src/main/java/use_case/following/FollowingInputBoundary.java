package use_case.following;

import use_case.account.AccountProfile;

public interface FollowingInputBoundary {
    void loadFollowing();

    void findAccount(String username);

    void follow(AccountProfile account);
}
