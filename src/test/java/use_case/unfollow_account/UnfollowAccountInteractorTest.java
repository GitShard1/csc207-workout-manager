package use_case.unfollow_account;

import entity.Follow;
import entity.User;
import org.junit.jupiter.api.Test;
import use_case.unfollow_account.UnfollowAccountDataAccessInterface.DeleteFailed;
import use_case.unfollow_account.UnfollowAccountOutputBoundary.Failure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class UnfollowAccountInteractorTest {
    private static final User OWNER = new User(UUID.randomUUID(), "owner", "Owner");
    private static final UUID TARGET = UUID.randomUUID();

    private final RecordingPresenter presenter = new RecordingPresenter();

    @Test
    void deletesOnlyTheSelectedBookmarkForTheActiveUserAndSucceedsWhenRetried() {
        final Follow selected = new Follow(OWNER.getUserID(), TARGET);
        final Follow anotherTarget = new Follow(OWNER.getUserID(), UUID.randomUUID());
        final Follow anotherOwner = new Follow(UUID.randomUUID(), TARGET);
        final Set<Follow> saved = new HashSet<>(List.of(selected, anotherTarget, anotherOwner));
        final var interactor = new UnfollowAccountInteractor(() -> Optional.of(OWNER),
                (viewer, followed) -> saved.remove(new Follow(viewer, followed)), presenter);
        final var input = new UnfollowAccountInputData(TARGET);

        interactor.execute(input);
        assertEquals(Set.of(anotherTarget, anotherOwner), saved);
        interactor.execute(input);

        final var result = new UnfollowAccountOutputData(TARGET);
        assertEquals(List.of(result, result), presenter.outcomes);
        assertEquals(Set.of(anotherTarget, anotherOwner), saved);
    }

    @Test
    void requiresAnActiveSessionBeforeDeleting() {
        final var interactor = new UnfollowAccountInteractor(Optional::empty,
                (viewer, followed) -> fail("Signed-out requests must not reach the DAO"), presenter);

        interactor.execute(new UnfollowAccountInputData(TARGET));

        assertEquals(List.of(Failure.SIGN_IN_REQUIRED), presenter.outcomes);
    }

    @Test
    void requiresASelectedAccountBeforeDeleting() {
        final var interactor = new UnfollowAccountInteractor(() -> Optional.of(OWNER),
                (viewer, followed) -> fail("Requests without a selection must not reach the DAO"), presenter);

        interactor.execute(new UnfollowAccountInputData(null));

        assertEquals(List.of(Failure.NO_ACCOUNT_SELECTED), presenter.outcomes);
    }

    @Test
    void reportsADeleteFailureThroughTheOutputBoundary() {
        final var interactor = new UnfollowAccountInteractor(() -> Optional.of(OWNER), (viewer, followed) -> {
            throw new DeleteFailed();
        }, presenter);

        interactor.execute(new UnfollowAccountInputData(TARGET));

        assertEquals(List.of(Failure.DELETE_FAILED), presenter.outcomes);
    }

    private static final class RecordingPresenter implements UnfollowAccountOutputBoundary {
        private final List<Object> outcomes = new ArrayList<>();

        @Override
        public void showUnfollowed(UnfollowAccountOutputData output) {
            outcomes.add(output);
        }

        @Override
        public void showUnfollowFailure(Failure failure) {
            outcomes.add(failure);
        }
    }
}
