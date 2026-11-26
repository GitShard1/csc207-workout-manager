package interface_adapter.following;

import org.junit.jupiter.api.Test;
import use_case.following.FindFollowAccountInputData;
import use_case.following.FollowAccountInputData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FollowingControllerTest {
    private final List<Object> calls = new ArrayList<>();
    private final FollowingController controller =
            new FollowingController(() -> calls.add("load"), calls::add, calls::add);

    @Test
    void loadsThroughTheLoadBoundary() {
        controller.loadFollowing();

        assertEquals(List.of("load"), calls);
    }

    @Test
    void forwardsTheExactUsernameThroughTheLookupBoundary() {
        controller.findAccount("  Alex  ");

        assertEquals(List.of(new FindFollowAccountInputData("  Alex  ")), calls);
    }

    @Test
    void forwardsOnlyTheSelectedIdThroughTheSaveBoundary() {
        final UUID selected = UUID.randomUUID();

        controller.follow(selected);

        assertEquals(List.of(new FollowAccountInputData(selected)), calls);
    }
}
