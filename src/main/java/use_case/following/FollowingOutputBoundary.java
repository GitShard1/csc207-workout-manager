package use_case.following;

public interface FollowingOutputBoundary {
    void showFollowing(FollowingListOutputData output);

    void showAccount(FollowingAccountOutputData output);

    void showSaved(FollowSavedOutputData output);

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
