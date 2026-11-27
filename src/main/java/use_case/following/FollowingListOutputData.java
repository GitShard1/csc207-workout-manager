package use_case.following;

import use_case.account.AccountProfile;

import java.util.List;

public record FollowingListOutputData(List<AccountProfile> accounts) {
    public FollowingListOutputData {
        accounts = List.copyOf(accounts);
    }
}
