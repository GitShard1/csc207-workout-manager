package use_case.following;

import use_case.account.AccountProfile;

import java.util.List;

public interface FollowingOutputBoundary {
    void showFollowing(List<AccountProfile> accounts);

    void showAccount(AccountProfile account);

    void showSaved(AccountProfile account, FollowingDataAccessInterface.SaveResult result);

    void showFailure(Failure failure);

    enum Failure {
        SIGN_IN_REQUIRED,
        ACCOUNT_NOT_FOUND,
        CANNOT_FOLLOW_SELF,
        LOAD_FAILED,
        LOOKUP_FAILED,
        SAVE_FAILED
    }
}
