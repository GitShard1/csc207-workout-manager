package entity;

import java.util.Objects;
import java.util.UUID;

/** An account's private, one-way bookmark of another account. */
public record Follow(UUID followerId, UUID followedId) {
    public Follow {
        Objects.requireNonNull(followerId, "followerId");
        Objects.requireNonNull(followedId, "followedId");
        if (followerId.equals(followedId)) {
            throw new IllegalArgumentException("An account cannot follow itself.");
        }
    }
}
