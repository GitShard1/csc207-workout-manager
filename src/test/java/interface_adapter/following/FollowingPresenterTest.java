package interface_adapter.following;

import interface_adapter.following.FollowingState.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import use_case.account.AccountProfile;
import use_case.following.FollowSavedOutputData;
import use_case.following.FollowingAccountOutputData;
import use_case.following.FollowingListOutputData;
import use_case.following.FollowingOutputBoundary.Failure;
import use_case.unfollow_account.UnfollowAccountOutputBoundary;
import use_case.unfollow_account.UnfollowAccountOutputData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FollowingPresenterTest {
    private static final UUID ALEX_ID = UUID.randomUUID();
    private static final UUID CASEY_ID = UUID.randomUUID();
    private static final AccountProfile ALEX = new AccountProfile(ALEX_ID, "alex_1", "Alex");
    private static final AccountProfile CASEY = new AccountProfile(CASEY_ID, "casey_1", "Casey");
    private static final Item ALEX_ROW = new Item(ALEX_ID, "alex_1", "@alex_1 - Alex");
    private static final Item CASEY_ROW = new Item(CASEY_ID, "casey_1", "@casey_1 - Casey");

    private final FollowingViewModel viewModel = new FollowingViewModel();
    private final FollowingPresenter presenter = new FollowingPresenter(viewModel);

    @Test
    void formatsTheLookupAndListAndKeepsTheirOrder() {
        presenter.showAccount(new FollowingAccountOutputData(ALEX));
        presenter.showFollowing(new FollowingListOutputData(List.of(CASEY, ALEX)));

        assertEquals(new FollowingState(ALEX_ROW, List.of(CASEY_ROW, ALEX_ROW), ""), viewModel.getState());
    }

    @Test
    void emptyListShowsTheEmptyStatusWithoutDroppingTheSelection() {
        presenter.showAccount(new FollowingAccountOutputData(ALEX));
        presenter.showFollowing(new FollowingListOutputData(List.of()));

        assertEquals(new FollowingState(ALEX_ROW, List.of(), "You aren't following anyone yet."),
                viewModel.getState());
    }

    @Test
    void savesTheSelectedProfileImmediatelyAheadOfExistingRows() {
        presenter.showFollowing(new FollowingListOutputData(List.of(CASEY)));
        presenter.showAccount(new FollowingAccountOutputData(ALEX));

        presenter.showSaved(new FollowSavedOutputData(ALEX_ID, false));

        assertEquals(new FollowingState(ALEX_ROW, List.of(ALEX_ROW, CASEY_ROW), "Now following @alex_1."),
                viewModel.getState());
    }

    @Test
    void duplicateIdsKeepTheirPositionEvenWhenTheAccountLabelChanged() {
        presenter.showFollowing(new FollowingListOutputData(List.of(CASEY, ALEX)));
        final Item renamed = new Item(ALEX_ID, "alex_1", "@alex_1 - Alexander");
        presenter.showAccount(new FollowingAccountOutputData(new AccountProfile(ALEX_ID, "alex_1", "Alexander")));

        presenter.showSaved(new FollowSavedOutputData(ALEX_ID, true));

        assertEquals(new FollowingState(renamed, List.of(CASEY_ROW, ALEX_ROW), "Already following"),
                viewModel.getState());
    }

    @Test
    void duplicateAddsTheCanonicalSelectionWhenTheLocalListHasNotCaughtUp() {
        presenter.showAccount(new FollowingAccountOutputData(ALEX));

        presenter.showSaved(new FollowSavedOutputData(ALEX_ID, true));

        assertEquals(new FollowingState(ALEX_ROW, List.of(ALEX_ROW), "Already following"), viewModel.getState());
    }

    @Test
    void completionForADifferentSelectionUsesAGenericConfirmationWithoutAddingTheWrongRow() {
        presenter.showFollowing(new FollowingListOutputData(List.of(CASEY)));
        presenter.showAccount(new FollowingAccountOutputData(ALEX));

        presenter.showSaved(new FollowSavedOutputData(CASEY_ID, false));

        assertEquals(new FollowingState(ALEX_ROW, List.of(CASEY_ROW), "Account followed."), viewModel.getState());
    }

    @Test
    void completionAfterTheSelectionIsClearedUsesAGenericConfirmation() {
        presenter.showSaved(new FollowSavedOutputData(ALEX_ID, false));

        assertEquals(new FollowingState(null, List.of(), "Account followed."), viewModel.getState());
    }

    @ParameterizedTest
    @CsvSource(value = {
        "ACCOUNT_NOT_FOUND | No account found for that username.",
        "LOOKUP_FAILED | Couldn't look up that account. Try again."
    }, delimiter = '|')
    void failedLookupsClearTheSelectionAndKeepBookmarks(Failure failure, String message) {
        viewModel.setState(new FollowingState(ALEX_ROW, List.of(CASEY_ROW), ""));

        presenter.showFailure(failure);

        assertEquals(new FollowingState(null, List.of(CASEY_ROW), message), viewModel.getState());
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CANNOT_FOLLOW_SELF | You can't follow yourself.",
        "LOAD_FAILED | Couldn't load Following. Try again.",
        "SAVE_FAILED | Couldn't save the follow. Try again."
    }, delimiter = '|')
    void retryableFailuresKeepTheSelectionAndBookmarks(Failure failure, String message) {
        viewModel.setState(new FollowingState(ALEX_ROW, List.of(CASEY_ROW), ""));

        presenter.showFailure(failure);

        assertEquals(new FollowingState(ALEX_ROW, List.of(CASEY_ROW), message), viewModel.getState());
    }

    @Test
    void signInRequiredClearsAllAccountDetails() {
        viewModel.setState(new FollowingState(ALEX_ROW, List.of(CASEY_ROW), ""));

        presenter.showFailure(Failure.SIGN_IN_REQUIRED);

        assertNull(viewModel.getState().result());
        assertEquals(List.of(), viewModel.getState().accounts());
        assertEquals("Sign in to use Following.", viewModel.getState().message());
    }

    @Test
    void unfollowRemovesTheMatchingIdAndKeepsTheSearchResultAndOtherRows() {
        final Item renamed = new Item(ALEX_ID, "alex_1", "@alex_1 - Alexander");
        viewModel.setState(new FollowingState(renamed, List.of(CASEY_ROW, ALEX_ROW), ""));

        presenter.showUnfollowed(new UnfollowAccountOutputData(ALEX_ID));

        assertEquals(new FollowingState(renamed, List.of(CASEY_ROW), "Account unfollowed."), viewModel.getState());
    }

    @ParameterizedTest
    @CsvSource(value = {
        "NO_ACCOUNT_SELECTED | Select an account to unfollow.",
        "DELETE_FAILED | Couldn't unfollow the account. Try again."
    }, delimiter = '|')
    void unfollowFailuresKeepTheSearchResultAndBookmarks(UnfollowAccountOutputBoundary.Failure failure,
                                                         String message) {
        viewModel.setState(new FollowingState(ALEX_ROW, List.of(CASEY_ROW, ALEX_ROW), ""));

        presenter.showUnfollowFailure(failure);

        assertEquals(new FollowingState(ALEX_ROW, List.of(CASEY_ROW, ALEX_ROW), message), viewModel.getState());
    }

    @Test
    void unfollowWithoutASessionClearsPrivateDetails() {
        viewModel.setState(new FollowingState(ALEX_ROW, List.of(CASEY_ROW), ""));

        presenter.showUnfollowFailure(UnfollowAccountOutputBoundary.Failure.SIGN_IN_REQUIRED);

        assertEquals(new FollowingState(null, List.of(), "Sign in to use Following."), viewModel.getState());
    }

    @Test
    void displayStateKeepsAnImmutableSnapshotOfTheRows() {
        final var rows = new ArrayList<>(List.of(ALEX_ROW));
        final var snapshot = new FollowingState(null, rows, "");
        rows.clear();

        assertEquals(List.of(ALEX_ROW), snapshot.accounts());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.accounts().clear());
    }
}
