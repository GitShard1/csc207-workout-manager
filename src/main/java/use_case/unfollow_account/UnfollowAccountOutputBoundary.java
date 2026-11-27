package use_case.unfollow_account;

public interface UnfollowAccountOutputBoundary {
    enum Failure {
        SIGN_IN_REQUIRED, NO_ACCOUNT_SELECTED, DELETE_FAILED
    }

    void showUnfollowed(UnfollowAccountOutputData output);

    void showUnfollowFailure(Failure failure);
}
