package interface_adapter.following;

import java.util.List;
import java.util.UUID;

public record FollowingState(Item result, List<Item> accounts, String message) {
    public FollowingState {
        accounts = List.copyOf(accounts);
    }

    public static FollowingState empty() {
        return new FollowingState(null, List.of(), "");
    }

    /** Display data derived from a profile returned by the account service. */
    public record Item(UUID accountId, String username, String label) { }
}
