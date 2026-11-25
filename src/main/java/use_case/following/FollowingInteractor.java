package use_case.following;

import entity.Follow;
import entity.User;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.following.FollowingDataAccessInterface.RequestFailed;
import use_case.following.FollowingDataAccessInterface.SaveResult;
import use_case.following.FollowingOutputBoundary.Failure;

import java.util.UUID;

public final class FollowingInteractor
        implements LoadFollowingInputBoundary, FindFollowAccountInputBoundary, FollowAccountInputBoundary {
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
                presenter.showFollowing(new FollowingListOutputData(bookmarks.list(user.getUserID())));
            } catch (RequestFailed ignored) {
                presenter.showFailure(Failure.LOAD_FAILED);
            }
        }, this::requireSignIn);
    }

    @Override
    public void findAccount(FindFollowAccountInputData input) {
        currentUser.currentUser().ifPresentOrElse(user -> findAccount(user, input.username()), this::requireSignIn);
    }

    private void findAccount(User viewer, String input) {
        final String username = input == null ? "" : input.strip();
        if (!User.isValidUsername(username)) {
            presenter.showFailure(Failure.ACCOUNT_NOT_FOUND);
            return;
        }
        try {
            bookmarks.findByUsername(viewer.getUserID(), username).ifPresentOrElse(
                    account -> presenter.showAccount(new FollowingAccountOutputData(account)),
                    () -> presenter.showFailure(Failure.ACCOUNT_NOT_FOUND));
        } catch (RequestFailed ignored) {
            presenter.showFailure(Failure.LOOKUP_FAILED);
        }
    }

    @Override
    public void follow(FollowAccountInputData input) {
        currentUser.currentUser().ifPresentOrElse(user -> follow(user, input.followedId()), this::requireSignIn);
    }

    private void follow(User viewer, UUID followedId) {
        if (followedId == null) {
            presenter.showFailure(Failure.ACCOUNT_NOT_FOUND);
            return;
        }
        final Follow follow;
        try {
            follow = new Follow(viewer.getUserID(), followedId);
        } catch (IllegalArgumentException ignored) {
            presenter.showFailure(Failure.CANNOT_FOLLOW_SELF);
            return;
        }
        try {
            final SaveResult result = bookmarks.save(follow);
            presenter.showSaved(new FollowSavedOutputData(followedId, result == SaveResult.ALREADY_FOLLOWING));
        } catch (RequestFailed ignored) {
            presenter.showFailure(Failure.SAVE_FAILED);
        }
    }

    private void requireSignIn() {
        presenter.showFailure(Failure.SIGN_IN_REQUIRED);
    }
}
