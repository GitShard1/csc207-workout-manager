package view;

import org.junit.jupiter.api.Test;

import javax.swing.SwingWorker;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static view.SwingTestSupport.onEdt;

class PlanViewRequestsTest {
    private final UUID viewer = UUID.randomUUID();
    private final AtomicLong session = new AtomicLong();
    private final PlanViewRequests requests = new PlanViewRequests(() -> viewer, session::get);

    @Test
    void staleWorkerCannotPublishSuccessFailureOrCompletionForTheSameAccountAfterSigningInAgain() throws Exception {
        assertDiscarded(false, session::incrementAndGet);
        assertDiscarded(true, session::incrementAndGet);
    }

    @Test
    void staleWorkerCannotPublishEvenWhenReenteringTheViewWithoutSwitchingAccounts() throws Exception {
        assertDiscarded(false, requests::invalidate);
        assertDiscarded(true, requests::invalidate);
    }

    private void assertDiscarded(boolean fail, Runnable invalidate) throws Exception {
        final CountDownLatch running = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch dispatched = new CountDownLatch(1);
        final List<String> effects = new CopyOnWriteArrayList<>();
        onEdt(() -> {
            final SwingWorker<String, Void> worker = requests.execute(requests.begin(), () -> {
                running.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                if (fail) {
                    throw new IllegalStateException("Old request failure");
                }
                return "Old request success";
            }, effects::add, error -> effects.add(error.getMessage()), () -> effects.add("Old request finished"));
            worker.addPropertyChangeListener(event -> {
                if ("state".equals(event.getPropertyName()) && event.getNewValue() == SwingWorker.StateValue.DONE) {
                    dispatched.countDown();
                }
            });
        });
        try {
            assertTrue(running.await(5, TimeUnit.SECONDS));
            onEdt(invalidate);
        } finally {
            release.countDown();
        }
        assertTrue(dispatched.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(), effects);
    }
}
