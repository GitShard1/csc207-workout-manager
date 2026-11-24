package use_case.following;

import use_case.account.AccountProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Accesses bookmarks and discoverable profiles as the signed-in account. */
public interface FollowingDataAccessInterface {
    List<AccountProfile> list(UUID viewerId) throws RequestFailed;

    Optional<AccountProfile> findByUsername(UUID viewerId, String username) throws RequestFailed;

    SaveResult save(UUID viewerId, UUID followedId) throws RequestFailed;

    enum SaveResult {
        SAVED,
        ALREADY_FOLLOWING
    }

    final class RequestFailed extends Exception { }
}
