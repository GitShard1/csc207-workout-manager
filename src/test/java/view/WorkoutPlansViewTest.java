package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.login.LogInViewModel;
import interface_adapter.logout.LogOutController;
import interface_adapter.plan_editor.PlanEditorState;
import interface_adapter.plan_editor.PlanEditorViewModel;
import interface_adapter.plan_sharing.PlanSharingController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import use_case.plan_sharing.PlanSharingException;
import use_case.plan_sharing.PlanSharingGateway;
import use_case.plan_sharing.PlanSharingInteractor;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static view.SwingTestSupport.await;
import static view.SwingTestSupport.button;
import static view.SwingTestSupport.field;
import static view.SwingTestSupport.find;
import static view.SwingTestSupport.label;
import static view.SwingTestSupport.onEdt;

class WorkoutPlansViewTest {
    private static final String CODE = "238b5b84-fded-4fda-9c3b-20a8a4454fef";
    private static final String REVISION = "a".repeat(64);
    private static final String OTHER_CODE = "bc4154b9-c13d-4c81-b9a3-918b1a609c3c";
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();
    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID COPY = UUID.randomUUID();
    private static final Instant EXPIRATION = Instant.now().plusSeconds(3600);

    private final AtomicReference<UUID> viewer = new AtomicReference<>(OWNER);
    private final AtomicLong session = new AtomicLong();
    private final ViewManagerModel navigation = new ViewManagerModel(HomeViewModel.VIEW_NAME);
    private final PlanEditorViewModel editor = new PlanEditorViewModel();
    private final FakeGateway gateway = new FakeGateway();
    private BiFunction<UUID, UUID, PlanEditorState> openPlan =
            (account, plan) -> new PlanEditorState(plan, "Push Day", List.of(), null);
    private WorkoutPlansView view;

    @BeforeEach
    void setUp() throws Exception {
        onEdt(() -> view = new WorkoutPlansView(new PlanSharingController(new PlanSharingInteractor(gateway)),
                (account, plan) -> openPlan.apply(account, plan),
                editor, navigation, viewer::get, session::get));
    }

    @Test
    void opensFromHomeAndReturnsThroughTheNormalViewManager() throws Exception {
        onEdt(() -> {
            final HomeView home = new HomeView(new HomeViewModel(), new LogOutController(() -> { }), navigation);
            final ViewManager views = new ViewManager(navigation);
            views.addView(HomeViewModel.VIEW_NAME, home);
            views.addView(WorkoutPlansView.VIEW_NAME, view);
            button(home, "Workout Plans").doClick(0);
            assertTrue(view.isVisible());
            assertFalse(home.isVisible());
        });
        await(() -> list().getModel().getSize() == 1);
        onEdt(() -> {
            action("Back to Home").doClick(0);
            assertEquals(HomeViewModel.VIEW_NAME, navigation.getState());
            assertFalse(view.isVisible());
            assertEquals(0, list().getModel().getSize());
        });
    }

    @Test
    void ownerGeneratesCodeAndRecipientPreviewsCopiesAndOpensTheSavedPlan() throws Exception {
        enter();
        onEdt(() -> {
            assertFalse(action("Generate share code").isEnabled());
            list().setSelectedIndex(0);
            action("Generate share code").doClick(0);
        });
        await(() -> action("Revoke code").isEnabled());
        onEdt(() -> {
            assertEquals(CODE, field(view, "Your share code").getText());
            assertFalse(field(view, "Your share code").isEditable());
            assertTrue(action("Copy code").isEnabled());
            assertTrue(label(view, "Share code expiration").getText().startsWith("Expires "));
        });

        switchToRecipient();
        onEdt(() -> {
            assertEquals("", field(view, "Your share code").getText());
            assertEquals(0, list().getModel().getSize());
        });
        preview();
        onEdt(() -> {
            assertEquals("Push Day", label(view, "Shared plan name").getText());
            final JTable table = find(view, JTable.class, item -> "Shared plan exercises".equals(item.getName()));
            assertEquals(2, table.getRowCount());
            assertEquals("Push-up", table.getValueAt(0, 0));
            assertEquals(3, table.getValueAt(0, 1));
            assertEquals(12, table.getValueAt(0, 2));
            assertNull(table.getValueAt(0, 3));
            assertEquals(17.5, table.getValueAt(1, 3));
            assertFalse(table.isCellEditable(0, 0));
            action("Save copy").doClick(0);
        });
        await(() -> status().equals("Saved Push Day (copy)."));
        onEdt(() -> {
            assertFalse(action("Save copy").isEnabled());
            assertEquals(new PlanSharingGateway.PlanSummary(COPY, "Push Day (copy)", null),
                    list().getSelectedValue());
            assertEquals(1, gateway.requests.size());
            action("Edit plan").doClick(0);
        });
        await(() -> PlanEditorViewModel.VIEW_NAME.equals(navigation.getState()));
        onEdt(() -> assertEquals(COPY, editor.getState().planId()));
    }

    @Test
    void ownerCanRevokeTheDisplayedCode() throws Exception {
        enter();
        onEdt(() -> {
            list().setSelectedIndex(0);
            action("Generate share code").doClick(0);
        });
        await(() -> action("Revoke code").isEnabled());
        onEdt(() -> action("Revoke code").doClick(0));
        await(() -> status().equals("Share code revoked."));
        onEdt(() -> {
            assertEquals("", field(view, "Your share code").getText());
            assertFalse(action("Revoke code").isEnabled());
            assertTrue(action("Generate share code").isEnabled());
        });
    }

    @Test
    void changedInputAndChangedSourceRequireAnotherPreview() throws Exception {
        switchToRecipient();
        preview();
        onEdt(() -> {
            field(view, "Share code").setText(OTHER_CODE);
            assertFalse(action("Save copy").isEnabled());
            assertTrue(label(view, "Shared plan name").getText().isBlank());
        });
        preview();
        gateway.copyFailure = PlanSharingException.Reason.STALE;
        onEdt(() -> action("Save copy").doClick(0));
        await(() -> status().startsWith("The shared plan changed."));
        onEdt(() -> assertFalse(action("Save copy").isEnabled()));
        preview();
        onEdt(() -> assertTrue(action("Save copy").isEnabled()));
    }

    @Test
    void saveRetryReusesRequestIdAndAListFailureDoesNotResubmitTheCopy() throws Exception {
        switchToRecipient();
        preview();
        gateway.copyFailure = PlanSharingException.Reason.REQUEST_FAILED;
        onEdt(() -> action("Save copy").doClick(0));
        await(() -> status().contains("Please try again.") && action("Save copy").isEnabled());
        gateway.failListAfterCopy = true;
        onEdt(() -> {
            action("Save copy").doClick(0);
            assertFalse(action("Save copy").isEnabled());
            action("Save copy").doClick(0);
        });
        await(() -> status().equals("Saved Push Day (copy). Refresh to update your plans."));
        onEdt(() -> {
            assertFalse(action("Save copy").isEnabled());
            assertEquals(2, gateway.requests.size());
            assertNotNull(gateway.requests.getFirst());
            assertEquals(gateway.requests.getFirst(), gateway.requests.getLast());
            assertEquals(1, list().getModel().getSize());
        });
    }

    @Test
    void switchingAccountsLoadsTheNewListWhilePreviousAccountIsWaiting() throws Exception {
        gateway.pauseOwnerList = true;
        try {
            onEdt(() -> navigation.setState(WorkoutPlansView.VIEW_NAME));
            assertTrue(gateway.ownerStarted.await(5, TimeUnit.SECONDS));
            switchToRecipient();
            onEdt(() -> {
                assertEquals(0, list().getModel().getSize());
                assertFalse(action("Edit plan").isEnabled());
                assertFalse(action("Generate share code").isEnabled());
            });
        } finally {
            gateway.releaseOwner.countDown();
        }
    }

    @Test
    void switchingSessionsDiscardsAnOpenEditorFromThePreviousSession() throws Exception {
        final CountDownLatch opening = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        openPlan = (account, plan) -> {
            opening.countDown();
            waitForRelease(release);
            return new PlanEditorState(plan, "Old owner plan", List.of(), null);
        };
        enter();
        try {
            onEdt(() -> {
                list().setSelectedIndex(0);
                action("Edit plan").doClick(0);
            });
            assertTrue(opening.await(5, TimeUnit.SECONDS));
            switchToRecipient();
        } finally {
            release.countDown();
        }
        onEdt(() -> {
            assertNotEquals(PlanEditorViewModel.VIEW_NAME, navigation.getState());
            assertTrue(editor.getState().isNewPlan());
        });
    }

    private void enter() throws Exception {
        onEdt(() -> navigation.setState(WorkoutPlansView.VIEW_NAME));
        await(() -> action("Refresh plans").isEnabled());
    }

    private void switchToRecipient() throws Exception {
        onEdt(() -> {
            navigation.setState(LogInViewModel.VIEW_NAME);
            viewer.set(RECIPIENT);
            session.incrementAndGet();
        });
        enter();
    }

    private void preview() throws Exception {
        onEdt(() -> {
            field(view, "Share code").setText(CODE);
            action("Preview plan").doClick(0);
        });
        await(() -> action("Save copy").isEnabled());
    }

    private JButton action(String name) {
        return button(view, name);
    }

    private JList<?> list() {
        return find(view, JList.class, item -> "Owned workout plans".equals(item.getName()));
    }

    private String status() {
        return label(view, "Workout plans status").getText();
    }

    private static void waitForRelease(CountDownLatch release) {
        try {
            assertTrue(release.await(5, TimeUnit.SECONDS), "The test did not release the previous request.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class FakeGateway implements PlanSharingGateway {
        private final CountDownLatch ownerStarted = new CountDownLatch(1);
        private final CountDownLatch releaseOwner = new CountDownLatch(1);
        private final List<UUID> requests = new ArrayList<>();
        private volatile boolean pauseOwnerList;
        private volatile boolean failListAfterCopy;
        private volatile PlanSharingException.Reason copyFailure;
        private volatile boolean saved;

        @Override
        public List<PlanSummary> list(UUID viewer) throws PlanSharingException {
            assertFalse(SwingUtilities.isEventDispatchThread());
            if (OWNER.equals(viewer)) {
                if (pauseOwnerList) {
                    ownerStarted.countDown();
                    waitForRelease(releaseOwner);
                }
                return List.of(new PlanSummary(SOURCE, "Push Day", null));
            }
            assertEquals(RECIPIENT, viewer);
            if (saved && failListAfterCopy) {
                throw new PlanSharingException(PlanSharingException.Reason.REQUEST_FAILED);
            }
            return saved ? List.of(new PlanSummary(COPY, "Push Day (copy)", null)) : List.of();
        }

        @Override
        public GeneratedCode generate(UUID viewer, UUID planId) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals(OWNER, viewer);
            assertEquals(SOURCE, planId);
            return new GeneratedCode(CODE, EXPIRATION);
        }

        @Override
        public void revoke(UUID viewer, UUID planId) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals(OWNER, viewer);
            assertEquals(SOURCE, planId);
        }

        @Override
        public Preview preview(UUID viewer, String code) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals(RECIPIENT, viewer);
            assertEquals(CODE, code);
            return new Preview("Push Day", List.of(
                    new ExerciseTarget("Push-up", 3, 12, null),
                    new ExerciseTarget("Chest press", 2, 8, 17.5)), REVISION);
        }

        @Override
        public CopyResult copy(UUID viewer, String code, String revision, UUID requestId)
                throws PlanSharingException {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals(RECIPIENT, viewer);
            assertEquals(CODE, code);
            assertEquals(REVISION, revision);
            requests.add(requestId);
            final PlanSharingException.Reason failure = copyFailure;
            copyFailure = null;
            if (failure != null) {
                throw new PlanSharingException(failure);
            }
            saved = true;
            return new CopyResult(COPY, "Push Day (copy)");
        }
    }
}
