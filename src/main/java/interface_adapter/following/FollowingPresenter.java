package interface_adapter.following;

import interface_adapter.following.FollowingState.Item;
import use_case.account.AccountProfile;
import use_case.following.FollowSavedOutputData;
import use_case.following.FollowingAccountOutputData;
import use_case.following.FollowingListOutputData;
import use_case.following.FollowingOutputBoundary;

import java.util.ArrayList;
import java.util.List;

public final class FollowingPresenter implements FollowingOutputBoundary {
    private final FollowingViewModel viewModel;

    public FollowingPresenter(FollowingViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void showFollowing(FollowingListOutputData output) {
        final List<Item> accounts = output.accounts().stream().map(FollowingPresenter::item).toList();
        final String message = accounts.isEmpty() ? "You aren't following anyone yet." : "";
        viewModel.setState(new FollowingState(viewModel.getState().result(), accounts, message));
    }

    @Override
    public void showAccount(FollowingAccountOutputData output) {
        viewModel.setState(new FollowingState(item(output.account()), viewModel.getState().accounts(), ""));
    }

    @Override
    public void showSaved(FollowSavedOutputData output) {
        final FollowingState state = viewModel.getState();
        final Item selected = state.result();
        final boolean matches = selected != null && selected.accountId().equals(output.followedId());
        final List<Item> accounts = new ArrayList<>(state.accounts());
        if (matches && accounts.stream().noneMatch(saved -> saved.accountId().equals(output.followedId()))) {
            accounts.addFirst(selected);
        }
        final String confirmation = matches ? "Now following @" + selected.username() + "." : "Account followed.";
        final String message = output.alreadyFollowing() ? "Already following" : confirmation;
        viewModel.setState(new FollowingState(selected, accounts, message));
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

    private static Item item(AccountProfile profile) {
        return new Item(profile.userID(), profile.username(), "@" + profile.username() + " - " + profile.displayName());
    }
}
