package interface_adapter.following;

import use_case.account.AccountProfile;
import use_case.following.FollowingDataAccessInterface.SaveResult;
import use_case.following.FollowingOutputBoundary;

import java.util.ArrayList;
import java.util.List;

public final class FollowingPresenter implements FollowingOutputBoundary {
    private final FollowingViewModel viewModel;

    public FollowingPresenter(FollowingViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void showFollowing(List<AccountProfile> accounts) {
        final String message = accounts.isEmpty() ? "You aren't following anyone yet." : "";
        viewModel.setState(new FollowingState(viewModel.getState().result(), accounts, message));
    }

    @Override
    public void showAccount(AccountProfile account) {
        viewModel.setState(new FollowingState(account, viewModel.getState().accounts(), ""));
    }

    @Override
    public void showSaved(AccountProfile account, SaveResult result) {
        final List<AccountProfile> accounts = new ArrayList<>(viewModel.getState().accounts());
        if (accounts.stream().noneMatch(saved -> saved.userID().equals(account.userID()))) {
            accounts.addFirst(account);
        }
        final String message = result == SaveResult.SAVED ? "Now following @" + account.username() + "."
                : "Already following";
        viewModel.setState(new FollowingState(account, accounts, message));
    }

    @Override
    public void showFailure(Failure failure) {
        final FollowingState state = viewModel.getState();
        final FollowingState next = switch (failure) {
            case SIGN_IN_REQUIRED -> new FollowingState(null, List.of(), "Sign in to use Following.");
            case ACCOUNT_NOT_FOUND -> new FollowingState(null, state.accounts(), "No account found for that username.");
            case LOOKUP_FAILED ->
                    new FollowingState(null, state.accounts(), "Couldn't look up that account. Try again.");
            case CANNOT_FOLLOW_SELF ->
                    new FollowingState(state.result(), state.accounts(), "You can't follow yourself.");
            case LOAD_FAILED ->
                    new FollowingState(state.result(), state.accounts(), "Couldn't load Following. Try again.");
            case SAVE_FAILED ->
                    new FollowingState(state.result(), state.accounts(), "Couldn't save the follow. Try again.");
        };
        viewModel.setState(next);
    }
}
