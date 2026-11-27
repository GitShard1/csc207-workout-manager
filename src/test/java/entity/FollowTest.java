package entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FollowTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void preservesTheDirectionOfTheBookmark() {
        final Follow follow = new Follow(owner, target);

        assertEquals(owner, follow.followerId());
        assertEquals(target, follow.followedId());
        assertEquals(follow, new Follow(owner, target));
        assertNotEquals(follow, new Follow(target, owner));
    }

    @Test
    void rejectsMissingIdsAndSelfFollows() {
        assertThrows(NullPointerException.class, () -> new Follow(null, target));
        assertThrows(NullPointerException.class, () -> new Follow(owner, null));
        assertThrows(IllegalArgumentException.class, () -> new Follow(owner, owner));
    }
}
