package use_case.unfollow_account;

import entity.User;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.unfollow_account.UnfollowAccountDataAccessInterface.DeleteFailed;
import use_case.unfollow_account.UnfollowAccountOutputBoundary.Failure;

import java.util.Optional;
import java.util.UUID;

/** Removes one private Following bookmark for the signed-in account. */
public final class UnfollowAccountInteractor implements UnfollowAccountInputBoundary {
    private final CurrentUserDataAccessInterface currentUser;
    private final UnfollowAccountDataAccessInterface bookmarks;
    private final UnfollowAccountOutputBoundary presenter;

    public UnfollowAccountInteractor(CurrentUserDataAccessInterface currentUser,
                                     UnfollowAccountDataAccessInterface bookmarks,
                                     UnfollowAccountOutputBoundary presenter) {
        this.currentUser = currentUser;
        this.bookmarks = bookmarks;
        this.presenter = presenter;
    }

    @Override
    public void execute(UnfollowAccountInputData input) {
        final Optional<User> viewer = currentUser.currentUser();
        if (viewer.isEmpty()) {
            presenter.showUnfollowFailure(Failure.SIGN_IN_REQUIRED);
            return;
        }

        final UUID followedId = input.followedId();
        if (followedId == null) {
            presenter.showUnfollowFailure(Failure.NO_ACCOUNT_SELECTED);
            return;
        }

        try {
            bookmarks.delete(viewer.get().getUserID(), followedId);
        } catch (DeleteFailed ignored) {
            presenter.showUnfollowFailure(Failure.DELETE_FAILED);
            return;
        }
        presenter.showUnfollowed(new UnfollowAccountOutputData(followedId));
    }
}
