package interface_adapter.following;

import use_case.account.AccountProfile;

import java.util.List;

public record FollowingState(AccountProfile result, List<AccountProfile> accounts, String message) {
    public FollowingState {
        accounts = List.copyOf(accounts);
    }

    public static FollowingState empty() {
        return new FollowingState(null, List.of(), "");
    }
}
