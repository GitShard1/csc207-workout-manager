package use_case.following;

import java.util.UUID;

/** Identifies the saved account without relying on profile information supplied by the view. */
public record FollowSavedOutputData(UUID followedId, boolean alreadyFollowing) { }
