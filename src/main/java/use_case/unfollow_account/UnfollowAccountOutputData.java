package use_case.unfollow_account;

import java.util.UUID;

/** Identifies the account whose bookmark is now absent from the viewer's list. */
public record UnfollowAccountOutputData(UUID followedId) { }
