package use_case.following;

import entity.Follow;
import use_case.account.AccountProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Accesses bookmarks and discoverable profiles as the signed-in account. */
public interface FollowingDataAccessInterface {
    /** Returns the viewer's bookmarks in display order, newest first. */
    List<AccountProfile> list(UUID viewerId) throws RequestFailed;

    /** Looks up an exact username. An empty result means the account was not found. */
    Optional<AccountProfile> findByUsername(UUID viewerId, String username) throws RequestFailed;

    /** Saves a bookmark once, reporting whether the same directed pair already exists. */
    SaveResult save(Follow follow) throws RequestFailed;

    enum SaveResult {
        SAVED,
        ALREADY_FOLLOWING
    }

    /** The operation could not be completed with a usable result. */
    final class RequestFailed extends Exception { }
}
