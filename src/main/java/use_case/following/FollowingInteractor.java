package use_case.following;

import entity.User;
import use_case.account.AccountProfile;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.following.FollowingDataAccessInterface.RequestFailed;
import use_case.following.FollowingOutputBoundary.Failure;

public final class FollowingInteractor implements FollowingInputBoundary {
    private final CurrentUserDataAccessInterface currentUser;
    private final FollowingDataAccessInterface bookmarks;
    private final FollowingOutputBoundary presenter;

    public FollowingInteractor(CurrentUserDataAccessInterface currentUser, FollowingDataAccessInterface bookmarks,
                               FollowingOutputBoundary presenter) {
        this.currentUser = currentUser;
        this.bookmarks = bookmarks;
        this.presenter = presenter;
    }

    @Override
    public void loadFollowing() {
        currentUser.currentUser().ifPresentOrElse(user -> {
            try {
                presenter.showFollowing(bookmarks.list(user.getUserID()));
            } catch (RequestFailed ignored) {
                presenter.showFailure(Failure.LOAD_FAILED);
            }
        }, this::requireSignIn);
    }

    @Override
    public void findAccount(String username) {
        currentUser.currentUser().ifPresentOrElse(user -> findAccount(user, username), this::requireSignIn);
    }

    private void findAccount(User viewer, String input) {
        final String username = input == null ? "" : input.strip();
        if (!User.isValidUsername(username)) {
            presenter.showFailure(Failure.ACCOUNT_NOT_FOUND);
            return;
        }
        try {
            bookmarks.findByUsername(viewer.getUserID(), username).ifPresentOrElse(presenter::showAccount,
                    () -> presenter.showFailure(Failure.ACCOUNT_NOT_FOUND));
        } catch (RequestFailed ignored) {
            presenter.showFailure(Failure.LOOKUP_FAILED);
        }
    }

    @Override
    public void follow(AccountProfile account) {
        currentUser.currentUser().ifPresentOrElse(user -> follow(user, account), this::requireSignIn);
    }

    private void follow(User viewer, AccountProfile account) {
        if (viewer.getUserID().equals(account.userID())) {
            presenter.showFailure(Failure.CANNOT_FOLLOW_SELF);
            return;
        }
        try {
            presenter.showSaved(account, bookmarks.save(viewer.getUserID(), account.userID()));
        } catch (RequestFailed ignored) {
            presenter.showFailure(Failure.SAVE_FAILED);
        }
    }

    private void requireSignIn() {
        presenter.showFailure(Failure.SIGN_IN_REQUIRED);
    }
}
