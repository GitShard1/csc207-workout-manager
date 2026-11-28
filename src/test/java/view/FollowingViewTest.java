package view;

import entity.Follow;
import entity.User;
import interface_adapter.ViewManagerModel;
import interface_adapter.following.FollowingController;
import interface_adapter.following.FollowingPresenter;
import interface_adapter.following.FollowingViewModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.login.LogInViewModel;
import interface_adapter.logout.LogOutController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import use_case.account.AccountProfile;
import use_case.following.FollowingDataAccessInterface;
import use_case.following.FollowingInteractor;
import use_case.unfollow_account.UnfollowAccountDataAccessInterface;
import use_case.unfollow_account.UnfollowAccountInteractor;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static view.SwingTestSupport.await;
import static view.SwingTestSupport.button;
import static view.SwingTestSupport.field;
import static view.SwingTestSupport.find;
import static view.SwingTestSupport.label;
import static view.SwingTestSupport.onEdt;

/** Exercises the Following screen with the real interactor and an in-memory DAO. */
class FollowingViewTest {
    private static final User SAM = new User(UUID.randomUUID(), "sam_1", "Sam");
    private static final AccountProfile ALEX = new AccountProfile(UUID.randomUUID(), "alex_1", "Alex");
    private static final User CASEY = new User(UUID.randomUUID(), "casey_1", "Casey");
    private static final AccountProfile CASEY_PROFILE = AccountProfile.from(CASEY);

    private final AtomicReference<User> currentUser = new AtomicReference<>(SAM);
    private final ViewManagerModel viewManager = new ViewManagerModel(HomeViewModel.VIEW_NAME);
    private final FollowingFixture bookmarks = new FollowingFixture();

    private ViewManager views;
    private HomeView home;
    private FollowingView following;

    @BeforeEach
    void buildViews() throws Exception {
        onEdt(() -> {
            final HomeViewModel homeModel = new HomeViewModel();
            home = new HomeView(homeModel, new LogOutController(() -> {
                currentUser.set(null);
                viewManager.setState(LogInViewModel.VIEW_NAME);
            }));
            homeModel.showSignedIn(AccountProfile.from(SAM));
            final FollowingViewModel followingModel = new FollowingViewModel();
            final var presenter = new FollowingPresenter(followingModel);
            final FollowingInteractor interactor = new FollowingInteractor(
                    () -> Optional.ofNullable(currentUser.get()), bookmarks, presenter);
            final var unfollow = new UnfollowAccountInteractor(
                    () -> Optional.ofNullable(currentUser.get()), bookmarks, presenter);
            final var controller = new FollowingController(interactor, interactor, interactor, unfollow);
            following = new FollowingView(followingModel, controller, viewManager);

            views = new ViewManager(viewManager);
            views.addView(LogInViewModel.VIEW_NAME, new JPanel());
            views.addSignedInView(HomeViewModel.VIEW_NAME, "Dashboard", home);
            views.addSignedInView(FollowingViewModel.VIEW_NAME, "Following", following);
        });
    }

    @Test
    void opensFollowingFromHomeAndSavesAnAccountOnce() throws Exception {
        openFollowing();
        onEdt(() -> {
            assertEquals("You aren't following anyone yet.", status());
            assertEquals(0, followingList().getModel().getSize());
        });
        search("  alex_1  ");
        onEdt(() -> {
            assertEquals("@alex_1 - Alex", label(following, "Account result").getText());
            followButton().doClick(0);
            assertEquals("Saving follow...", status());
        });
        await(() -> followButton().isEnabled());
        onEdt(() -> {
            assertEquals("Now following @alex_1.", status());
            assertEquals("@alex_1 - Alex", followingList().getModel().getElementAt(0));
            followButton().doClick(0);
        });
        await(() -> followButton().isEnabled());
        onEdt(() -> {
            assertEquals("Already following", status());
            assertEquals(1, followingList().getModel().getSize());
            sidebarButton("Dashboard").doClick(0);
            assertTrue(home.isVisible());
            assertEquals(0, followingList().getModel().getSize());
        });

        openFollowing();

        onEdt(() -> {
            assertEquals(1, followingList().getModel().getSize());
            assertEquals("", field(following, "Username").getText());
            assertFalse(followButton().isVisible());
        });
    }

    @Test
    void changingTheSearchFieldDoesNotChangeTheDisplayedAccountToFollow() throws Exception {
        openFollowing();
        search("alex_1");

        onEdt(() -> {
            field(following, "Username").setText("casey_1");
            followButton().doClick(0);
        });
        await(() -> followButton().isEnabled());

        assertEquals(List.of(ALEX), bookmarks.saved.get(SAM.getUserID()));
        onEdt(() -> assertEquals("Now following @alex_1.", status()));
    }

    @Test
    void reportsSelfFollowAndUnknownUsernameWithoutASave() throws Exception {
        openFollowing();
        search("sam_1");
        onEdt(() -> followButton().doClick(0));
        await(() -> followButton().isEnabled());
        onEdt(() -> {
            assertEquals("You can't follow yourself.", status());
            assertEquals(0, followingList().getModel().getSize());
        });

        search("missing");

        onEdt(() -> {
            assertEquals("No account found for that username.", status());
            assertFalse(followButton().isVisible());
            followButton().doClick(0);
        });
        assertEquals(0, bookmarks.saveCalls.get());
    }

    @Test
    void blocksRepeatedClicksWhileSavingAndAllowsRetryAfterFailure() throws Exception {
        openFollowing();
        search("alex_1");
        final CountDownLatch releaseSave = new CountDownLatch(1);
        bookmarks.releaseSave = releaseSave;
        bookmarks.failSave = true;

        try {
            onEdt(() -> followButton().doClick(0));
            assertTrue(bookmarks.saveStarted.await(5, TimeUnit.SECONDS), "The save should start off the EDT");
            onEdt(() -> {
                assertFalse(followButton().isEnabled());
                assertFalse(button(following, "Search").isEnabled());
                assertFalse(button(following, "Refresh").isEnabled());
                assertFalse(field(following, "Username").isEnabled());
                assertEquals("Saving follow...", status());
                followButton().doClick(0);
            });
            assertEquals(1, bookmarks.saveCalls.get());
        } finally {
            releaseSave.countDown();
        }
        await(() -> followButton().isEnabled());
        onEdt(() -> {
            assertEquals("Couldn't save the follow. Try again.", status());
            assertEquals(0, followingList().getModel().getSize());
            assertEquals("@alex_1 - Alex", label(following, "Account result").getText());
            bookmarks.failSave = false;
            followButton().doClick(0);
        });
        await(() -> followButton().isEnabled());

        assertEquals(2, bookmarks.saveCalls.get());
        onEdt(() -> {
            assertEquals("Now following @alex_1.", status());
            assertEquals(1, followingList().getModel().getSize());
        });
    }

    @Test
    void unfollowsTheSelectedBookmarkAndCanFollowItAgainAfterSigningBackIn() throws Exception {
        bookmarks.saved.put(SAM.getUserID(), List.of(ALEX, CASEY_PROFILE));
        bookmarks.saved.put(CASEY.getUserID(), List.of(ALEX));
        openFollowing();
        search("casey_1");
        onEdt(() -> {
            assertFalse(unfollowButton().isEnabled());
            unfollowButton().doClick(0);
            assertEquals(0, bookmarks.deleteCalls.get());
            followingList().setSelectedIndex(0);
            assertTrue(unfollowButton().isEnabled());
            unfollowButton().doClick(0);
        });
        await(() -> button(following, "Refresh").isEnabled());

        assertEquals(List.of(CASEY_PROFILE), bookmarks.saved.get(SAM.getUserID()));
        assertEquals(List.of(ALEX), bookmarks.saved.get(CASEY.getUserID()));
        onEdt(() -> {
            assertEquals("Account unfollowed.", status());
            assertOnlyCaseyIsBookmarked();
            assertFalse(unfollowButton().isEnabled());
            button(following, "Refresh").doClick(0);
        });
        await(() -> button(following, "Refresh").isEnabled());
        onEdt(() -> {
            assertOnlyCaseyIsBookmarked();
            sidebarButton("Dashboard").doClick(0);
            button(home, "Log Out").doClick(0);
        });
        await(() -> LogInViewModel.VIEW_NAME.equals(viewManager.getState())
                && button(home, "Log Out").isEnabled());
        onEdt(() -> {
            currentUser.set(SAM);
            viewManager.setState(HomeViewModel.VIEW_NAME);
        });
        openFollowing();
        onEdt(this::assertOnlyCaseyIsBookmarked);

        search("alex_1");
        onEdt(() -> followButton().doClick(0));
        await(() -> followButton().isEnabled());
        assertEquals(List.of(ALEX, CASEY_PROFILE), bookmarks.saved.get(SAM.getUserID()));
        onEdt(() -> {
            assertEquals("Now following @alex_1.", status());
            assertEquals("@alex_1 - Alex", followingList().getModel().getElementAt(0));
        });
    }

    @Test
    void blocksDuplicateUnfollowsAndKeepsTheRowSelectedForRetryAfterFailure() throws Exception {
        bookmarks.saved.put(SAM.getUserID(), List.of(CASEY_PROFILE, ALEX));
        openFollowing();
        final CountDownLatch releaseDelete = new CountDownLatch(1);
        bookmarks.releaseDelete = releaseDelete;
        bookmarks.failDelete = true;

        try {
            onEdt(() -> {
                followingList().setSelectedIndex(1);
                unfollowButton().doClick(0);
            });
            assertTrue(bookmarks.deleteStarted.await(5, TimeUnit.SECONDS), "Unfollow should start off the EDT");
            onEdt(() -> {
                assertEquals("Removing follow...", status());
                assertFalse(unfollowButton().isEnabled());
                assertFalse(followingList().isEnabled());
                assertFalse(button(following, "Refresh").isEnabled());
                unfollowButton().doClick(0);
            });
            assertEquals(1, bookmarks.deleteCalls.get());
        } finally {
            releaseDelete.countDown();
        }
        await(() -> unfollowButton().isEnabled());
        onEdt(() -> {
            assertEquals("Couldn't unfollow the account. Try again.", status());
            assertEquals("@alex_1 - Alex", followingList().getSelectedValue());
            assertEquals(2, followingList().getModel().getSize());
            bookmarks.failDelete = false;
            unfollowButton().doClick(0);
        });
        await(() -> button(following, "Refresh").isEnabled());

        assertEquals(2, bookmarks.deleteCalls.get());
        onEdt(() -> {
            assertEquals("Account unfollowed.", status());
            assertOnlyCaseyIsBookmarked();
        });
    }

    @Test
    void clearsAStaleRowWhenTheBookmarkIsAlreadyAbsent() throws Exception {
        bookmarks.saved.put(SAM.getUserID(), List.of(ALEX));
        openFollowing();
        bookmarks.saved.remove(SAM.getUserID());

        onEdt(() -> {
            followingList().setSelectedIndex(0);
            unfollowButton().doClick(0);
        });
        await(() -> button(following, "Refresh").isEnabled());
        onEdt(() -> {
            assertEquals("Account unfollowed.", status());
            assertEquals(0, followingList().getModel().getSize());
        });
    }

    private void assertOnlyCaseyIsBookmarked() {
        assertEquals(1, followingList().getModel().getSize());
        assertEquals("@casey_1 - Casey", followingList().getModel().getElementAt(0));
    }

    @Test
    void failedListLoadCanBeRetried() throws Exception {
        bookmarks.failList = true;
        openFollowing();
        onEdt(() -> {
            assertEquals("Couldn't load Following. Try again.", status());
            bookmarks.failList = false;
            button(following, "Refresh").doClick(0);
        });
        await(() -> button(following, "Refresh").isEnabled());
        onEdt(() -> assertEquals("You aren't following anyone yet.", status()));
    }

    @Test
    void unexpectedLoadFailureReEnablesControlsAndCanBeRetried() throws Exception {
        bookmarks.failUnexpectedly = true;
        openFollowing();
        onEdt(() -> {
            assertEquals("Something went wrong. Try again.", status());
            assertTrue(button(following, "Refresh").isEnabled());
            bookmarks.failUnexpectedly = false;
            button(following, "Refresh").doClick(0);
        });

        await(() -> button(following, "Refresh").isEnabled());

        onEdt(() -> assertEquals("You aren't following anyone yet.", status()));
    }

    @Test
    void failedLookupClearsTheOldResultAndEnterRetriesWithoutLosingBookmarks() throws Exception {
        bookmarks.saved.put(SAM.getUserID(), List.of(ALEX));
        openFollowing();
        search("sam_1");
        onEdt(() -> assertEquals("@sam_1 - Sam", label(following, "Account result").getText()));

        bookmarks.failLookup = true;
        search("alex_1");
        onEdt(() -> {
            assertEquals("Couldn't look up that account. Try again.", status());
            assertFalse(followButton().isVisible());
            assertEquals("", label(following, "Account result").getText());
            assertEquals("@alex_1 - Alex", followingList().getModel().getElementAt(0));
            bookmarks.failLookup = false;
            field(following, "Username").postActionEvent();
        });
        await(() -> button(following, "Search").isEnabled());

        onEdt(() -> {
            assertEquals("@alex_1 - Alex", label(following, "Account result").getText());
            assertTrue(followButton().isVisible());
            assertEquals(1, followingList().getModel().getSize());
        });
    }

    @Test
    void reopeningAfterAnAccountSwitchShowsOnlyTheNewOwnersBookmarks() throws Exception {
        bookmarks.saved.put(SAM.getUserID(), List.of(ALEX));
        bookmarks.saved.put(CASEY.getUserID(), List.of(AccountProfile.from(SAM)));
        openFollowing();
        search("alex_1");
        onEdt(() -> {
            assertEquals("@alex_1 - Alex", followingList().getModel().getElementAt(0));
            sidebarButton("Dashboard").doClick(0);
            button(home, "Log Out").doClick(0);
        });
        await(() -> LogInViewModel.VIEW_NAME.equals(viewManager.getState())
                && button(home, "Log Out").isEnabled());
        onEdt(() -> {
            currentUser.set(CASEY);
            viewManager.setState(HomeViewModel.VIEW_NAME);
        });

        openFollowing();

        onEdt(() -> {
            assertEquals(1, followingList().getModel().getSize());
            assertEquals("@sam_1 - Sam", followingList().getModel().getElementAt(0));
            assertEquals("", label(following, "Account result").getText());
            assertFalse(followButton().isVisible());
        });
    }

    @Test
    void leavingThroughTheSidebarHidesTheAccountsFromTheNextUser() throws Exception {
        bookmarks.saved.put(SAM.getUserID(), List.of(ALEX));
        openFollowing();
        search("alex_1");
        onEdt(() -> {
            sidebarButton("Dashboard").doClick(0);
            button(home, "Log Out").doClick(0);
        });
        await(() -> LogInViewModel.VIEW_NAME.equals(viewManager.getState())
                && button(home, "Log Out").isEnabled());
        bookmarks.failList = true;
        onEdt(() -> {
            currentUser.set(CASEY);
            viewManager.setState(HomeViewModel.VIEW_NAME);
            sidebarButton("Following").doClick(0);
        });
        await(() -> button(following, "Search").isEnabled());

        onEdt(() -> {
            assertEquals("Couldn't load Following. Try again.", status());
            assertEquals(0, followingList().getModel().getSize());
            assertEquals("", label(following, "Account result").getText());
            assertFalse(followButton().isVisible());
            assertEquals("", field(following, "Username").getText());
        });
    }

    @Test
    void aLateLoadFromThePreviousAccountIsDroppedAndTheNewAccountLoadsItsOwn() throws Exception {
        bookmarks.saved.put(SAM.getUserID(), List.of(ALEX));
        bookmarks.saved.put(CASEY.getUserID(), List.of(AccountProfile.from(SAM)));
        final CountDownLatch releaseSamsLoad = new CountDownLatch(1);
        bookmarks.releaseList = releaseSamsLoad;

        // Sam opens Following, but the load gets stuck on the network
        onEdt(() -> sidebarButton("Following").doClick(0));
        assertTrue(bookmarks.listStarted.await(5, TimeUnit.SECONDS));

        // Casey takes over and opens Following while Sam's load is still stuck
        onEdt(() -> {
            sidebarButton("Dashboard").doClick(0);
            currentUser.set(CASEY);
            bookmarks.releaseList = null;
            sidebarButton("Following").doClick(0);
        });
        releaseSamsLoad.countDown();

        // Sam's late result is thrown away, and Casey's own bookmarks load
        await(() -> button(following, "Search").isEnabled() && followingList().getModel().getSize() == 1
                && "@sam_1 - Sam".equals(followingList().getModel().getElementAt(0)));
        onEdt(() -> assertEquals("", label(following, "Account result").getText()));
    }

    private void openFollowing() throws Exception {
        onEdt(() -> {
            sidebarButton("Following").doClick(0);
            assertTrue(following.isVisible());
            assertEquals("Loading Following...", status());
        });
        await(() -> button(following, "Search").isEnabled());
    }

    private void search(String username) throws Exception {
        onEdt(() -> {
            field(following, "Username").setText(username);
            button(following, "Search").doClick(0);
        });
        await(() -> button(following, "Search").isEnabled());
    }

    private JButton followButton() {
        return button(following, "Follow");
    }

    private JButton unfollowButton() {
        return button(following, "Unfollow");
    }

    private String status() {
        return label(following, "Following status").getText();
    }

    private JToggleButton sidebarButton(String label) {
        return find(views, JToggleButton.class, button -> label.equals(button.getText()));
    }

    private JList<?> followingList() {
        return find(following, JList.class, list -> true);
    }

    private final class FollowingFixture implements FollowingDataAccessInterface, UnfollowAccountDataAccessInterface {
        private final List<AccountProfile> profiles = List.of(AccountProfile.from(SAM), ALEX, CASEY_PROFILE);
        private final Map<UUID, List<AccountProfile>> saved = new ConcurrentHashMap<>();
        private final AtomicInteger saveCalls = new AtomicInteger();
        private final CountDownLatch saveStarted = new CountDownLatch(1);
        private volatile CountDownLatch releaseSave;
        private volatile boolean failLookup;
        private volatile boolean failSave;
        private volatile boolean failList;
        private volatile boolean failUnexpectedly;
        private final AtomicInteger deleteCalls = new AtomicInteger();
        private final CountDownLatch deleteStarted = new CountDownLatch(1);
        private volatile CountDownLatch releaseDelete;
        private volatile boolean failDelete;
        private final CountDownLatch listStarted = new CountDownLatch(1);
        private volatile CountDownLatch releaseList;

        @Override
        public List<AccountProfile> list(UUID viewerId) throws RequestFailed {
            assertViewer(viewerId);
            if (failUnexpectedly) {
                throw new IllegalStateException("Following test fixture failure");
            }
            if (failList) {
                throw new RequestFailed();
            }
            final CountDownLatch release = releaseList;
            if (release != null) {
                listStarted.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS), "The test should release the list");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    throw new RequestFailed();
                }
            }
            return saved.getOrDefault(viewerId, List.of());
        }

        @Override
        public Optional<AccountProfile> findByUsername(UUID viewerId, String username) throws RequestFailed {
            assertViewer(viewerId);
            if (failLookup) {
                throw new RequestFailed();
            }
            return profiles.stream().filter(account -> account.username().equals(username)).findFirst();
        }

        @Override
        public SaveResult save(Follow follow) throws RequestFailed {
            assertViewer(follow.followerId());
            saveCalls.incrementAndGet();
            saveStarted.countDown();
            waitForSaveRelease();
            if (failSave) {
                throw new RequestFailed();
            }
            final var existing = saved.getOrDefault(follow.followerId(), List.of());
            if (existing.stream().anyMatch(account -> account.userID().equals(follow.followedId()))) {
                return SaveResult.ALREADY_FOLLOWING;
            }
            final var target = profiles.stream().filter(account -> account.userID().equals(follow.followedId()))
                    .findFirst().orElseThrow(RequestFailed::new);
            final var updated = new ArrayList<>(existing);
            updated.addFirst(target);
            saved.put(follow.followerId(), List.copyOf(updated));
            return SaveResult.SAVED;
        }

        @Override
        public void delete(UUID viewerId, UUID followedId) throws DeleteFailed {
            assertViewer(viewerId);
            deleteCalls.incrementAndGet();
            deleteStarted.countDown();
            final CountDownLatch release = releaseDelete;
            if (release != null) {
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS), "The test should release the delete");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    throw new DeleteFailed();
                }
            }
            if (failDelete) {
                throw new DeleteFailed();
            }
            saved.computeIfPresent(viewerId, (owner, accounts) -> accounts.stream()
                    .filter(account -> !account.userID().equals(followedId)).toList());
        }

        private void waitForSaveRelease() throws RequestFailed {
            final CountDownLatch release = releaseSave;
            if (release != null) {
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS), "The test should release the save");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    throw new RequestFailed();
                }
            }
        }

        private void assertViewer(UUID viewerId) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals(currentUser.get().getUserID(), viewerId);
        }
    }
}
