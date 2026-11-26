package use_case.following;

import entity.Follow;
import entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import use_case.account.AccountProfile;
import use_case.following.FollowingOutputBoundary.Failure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowingInteractorTest {
    private static final User OWNER = new User(UUID.randomUUID(), "owner", "Owner");
    private static final AccountProfile ALEX = new AccountProfile(UUID.randomUUID(), "alex_1", "Alex");
    private static final AccountProfile CASEY = new AccountProfile(UUID.randomUUID(), "casey_1", "Casey");
    private static final UUID OWNER_ID = OWNER.getUserID();

    private final InMemoryBookmarks bookmarks = new InMemoryBookmarks();
    private final RecordingPresenter presenter = new RecordingPresenter();
    private Optional<User> signedIn = Optional.of(OWNER);
    private final FollowingInteractor interactor = new FollowingInteractor(() -> signedIn, bookmarks, presenter);

    @Test
    void loadsAnEmptyListForTheCurrentOwner() {
        interactor.loadFollowing();

        assertEquals(List.of(new FollowingListOutputData(List.of())), presenter.events);
        assertEquals(List.of(OWNER_ID), bookmarks.calls);
    }

    @Test
    void preservesListOrderAndCopiesTheOutput() {
        bookmarks.saved.add(new Follow(OWNER_ID, CASEY.userID()));
        bookmarks.saved.add(new Follow(OWNER_ID, ALEX.userID()));
        bookmarks.saved.add(new Follow(ALEX.userID(), CASEY.userID()));

        interactor.loadFollowing();

        final var output = new FollowingListOutputData(List.of(CASEY, ALEX));
        assertEquals(List.of(output), presenter.events);
        assertEquals(List.of(OWNER_ID), bookmarks.calls);
        final var mutable = new ArrayList<>(List.of(CASEY, ALEX));
        final var snapshot = new FollowingListOutputData(mutable);
        mutable.clear();
        assertEquals(output, snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.accounts().clear());
    }

    @ParameterizedTest
    @CsvSource({"abc, abc", "abcdefghijklmnopqrstuvwx, abcdefghijklmnopqrstuvwx", "'  edge_1  ', edge_1"})
    void looksUpAnExactValidUsernameWithinTheLengthLimits(String input, String expected) {
        final var account = new AccountProfile(UUID.randomUUID(), expected, "Match");
        bookmarks.accounts.add(account);

        interactor.findAccount(new FindFollowAccountInputData(input));

        assertEquals(List.of(new FollowingAccountOutputData(account)), presenter.events);
        assertEquals(List.of(new Lookup(OWNER_ID, expected)), bookmarks.calls);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ab", "abcdefghijklmnopqrstuvwxy", "Alex", "alex smith", "alex-1", "aléx"})
    void rejectsInvalidUsernamesWithoutLookingThemUp(String input) {
        interactor.findAccount(new FindFollowAccountInputData(input));

        assertEquals(List.of(Failure.ACCOUNT_NOT_FOUND), presenter.events);
        assertTrue(bookmarks.calls.isEmpty());
    }

    @Test
    void reportsAnUnknownUsernameAfterOneLookup() {
        interactor.findAccount(new FindFollowAccountInputData("missing"));

        assertEquals(List.of(Failure.ACCOUNT_NOT_FOUND), presenter.events);
        assertEquals(List.of(new Lookup(OWNER_ID, "missing")), bookmarks.calls);
    }

    @Test
    void savesTheSelectedIdForTheSignedInOwner() {
        interactor.follow(new FollowAccountInputData(ALEX.userID()));

        final Follow pair = new Follow(OWNER_ID, ALEX.userID());
        assertEquals(List.of(new FollowSavedOutputData(ALEX.userID(), false)), presenter.events);
        assertEquals(List.of(pair), bookmarks.calls);
        assertEquals(List.of(pair), List.copyOf(bookmarks.saved));
    }

    @Test
    void reportsADuplicateWithoutAddingAnotherBookmark() {
        final Follow pair = new Follow(OWNER_ID, ALEX.userID());
        bookmarks.saved.add(pair);

        interactor.follow(new FollowAccountInputData(ALEX.userID()));

        assertEquals(List.of(new FollowSavedOutputData(ALEX.userID(), true)), presenter.events);
        assertEquals(List.of(pair), bookmarks.calls);
        assertEquals(List.of(pair), List.copyOf(bookmarks.saved));
    }

    @Test
    void reportsSaveFailureIfTheSelectedAccountDisappearsAfterLookup() {
        interactor.findAccount(new FindFollowAccountInputData("alex_1"));
        bookmarks.accounts.remove(ALEX);

        interactor.follow(new FollowAccountInputData(ALEX.userID()));

        assertEquals(List.of(new FollowingAccountOutputData(ALEX), Failure.SAVE_FAILED), presenter.events);
        assertEquals(List.of(new Lookup(OWNER_ID, "alex_1"), new Follow(OWNER_ID, ALEX.userID())), bookmarks.calls);
        assertTrue(bookmarks.saved.isEmpty());
    }

    @Test
    void rejectsASelfFollowWithoutSaving() {
        interactor.follow(new FollowAccountInputData(OWNER_ID));

        assertEquals(List.of(Failure.CANNOT_FOLLOW_SELF), presenter.events);
        assertTrue(bookmarks.calls.isEmpty());
    }

    @Test
    void rejectsAMissingTargetWithoutSaving() {
        interactor.follow(new FollowAccountInputData(null));

        assertEquals(List.of(Failure.ACCOUNT_NOT_FOUND), presenter.events);
        assertTrue(bookmarks.calls.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void requiresSignInBeforeEveryAction(Action action) {
        signedIn = Optional.empty();

        perform(action);

        assertEquals(List.of(Failure.SIGN_IN_REQUIRED), presenter.events);
        assertTrue(bookmarks.calls.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void reportsTheSpecificFailureForEachDaoOperation(Action action) {
        bookmarks.failing = action;

        perform(action);

        switch (action) {
            case LOAD -> {
                assertEquals(List.of(Failure.LOAD_FAILED), presenter.events);
                assertEquals(List.of(OWNER_ID), bookmarks.calls);
            }
            case LOOKUP -> {
                assertEquals(List.of(Failure.LOOKUP_FAILED), presenter.events);
                assertEquals(List.of(new Lookup(OWNER_ID, "alex_1")), bookmarks.calls);
            }
            case SAVE -> {
                assertEquals(List.of(Failure.SAVE_FAILED), presenter.events);
                assertEquals(List.of(new Follow(OWNER_ID, ALEX.userID())), bookmarks.calls);
            }
        }
        assertTrue(bookmarks.saved.isEmpty());
    }

    private void perform(Action action) {
        switch (action) {
            case LOAD -> interactor.loadFollowing();
            case LOOKUP -> interactor.findAccount(new FindFollowAccountInputData("alex_1"));
            case SAVE -> interactor.follow(new FollowAccountInputData(ALEX.userID()));
        }
    }

    private enum Action { LOAD, LOOKUP, SAVE }

    private record Lookup(UUID owner, String username) { }

    private static final class RecordingPresenter implements FollowingOutputBoundary {
        private final List<Object> events = new ArrayList<>();

        @Override
        public void showFollowing(FollowingListOutputData output) {
            events.add(output);
        }

        @Override
        public void showAccount(FollowingAccountOutputData output) {
            events.add(output);
        }

        @Override
        public void showSaved(FollowSavedOutputData output) {
            events.add(output);
        }

        @Override
        public void showFailure(Failure failure) {
            events.add(failure);
        }
    }

    private static final class InMemoryBookmarks implements FollowingDataAccessInterface {
        private final List<AccountProfile> accounts = new ArrayList<>(List.of(ALEX, CASEY));
        private final SequencedSet<Follow> saved = new LinkedHashSet<>();
        private final List<Object> calls = new ArrayList<>();
        private Action failing;

        @Override
        public List<AccountProfile> list(UUID viewerId) throws RequestFailed {
            calls.add(viewerId);
            failIfRequested(Action.LOAD);
            return saved.stream().filter(follow -> viewerId.equals(follow.followerId()))
                    .flatMap(follow -> accounts.stream()
                            .filter(account -> follow.followedId().equals(account.userID())))
                    .toList();
        }

        @Override
        public Optional<AccountProfile> findByUsername(UUID viewerId, String username) throws RequestFailed {
            calls.add(new Lookup(viewerId, username));
            failIfRequested(Action.LOOKUP);
            return accounts.stream().filter(account -> username.equals(account.username())).findFirst();
        }

        @Override
        public SaveResult save(Follow follow) throws RequestFailed {
            calls.add(follow);
            failIfRequested(Action.SAVE);
            if (accounts.stream().noneMatch(account -> follow.followedId().equals(account.userID()))) {
                throw new RequestFailed();
            }
            if (saved.contains(follow)) {
                return SaveResult.ALREADY_FOLLOWING;
            }
            saved.addFirst(follow);
            return SaveResult.SAVED;
        }

        private void failIfRequested(Action action) throws RequestFailed {
            if (failing == action) {
                throw new RequestFailed();
            }
        }
    }
}
