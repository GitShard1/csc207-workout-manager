package use_case.unfollow_account;

import java.util.UUID;

public interface UnfollowAccountDataAccessInterface {
    /** Ensures this bookmark is absent from the viewer's list, including if it was already absent. */
    void delete(UUID viewerId, UUID followedId) throws DeleteFailed;

    final class DeleteFailed extends Exception { }
}
