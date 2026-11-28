package view;

import javax.swing.SwingWorker;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Delivers a worker's result on the EDT only while its view and sign-in are current. */
final class PlanViewRequests {
    private final Supplier<UUID> currentUser;
    private final LongSupplier sessionGeneration;
    private long viewGeneration;

    PlanViewRequests(Supplier<UUID> currentUser, LongSupplier sessionGeneration) {
        this.currentUser = currentUser;
        this.sessionGeneration = sessionGeneration;
    }

    void invalidate() {
        viewGeneration++;
    }

    Request begin() {
        final long signIn = sessionGeneration.getAsLong();
        return new Request(currentUser.get(), signIn, viewGeneration);
    }

    private boolean isCurrent(Request request) {
        return request.viewGeneration() == viewGeneration && isSameSession(request);
    }

    <T> SwingWorker<T, Void> execute(Request request, Callable<T> operation, Consumer<T> success,
                                    Consumer<Throwable> failure, Runnable finished) {
        final SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                if (!isSameSession(request)) {
                    return null;
                }
                return operation.call();
            }

            @Override
            protected void done() {
                if (!isCurrent(request)) {
                    return;
                }
                try {
                    success.accept(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failure.accept(exception);
                } catch (ExecutionException exception) {
                    failure.accept(exception.getCause());
                } finally {
                    if (isCurrent(request)) {
                        finished.run();
                    }
                }
            }
        };
        worker.execute();
        return worker;
    }

    private boolean isSameSession(Request request) {
        return request.viewer() != null && request.sessionGeneration() == sessionGeneration.getAsLong()
                && request.viewer().equals(currentUser.get());
    }

    record Request(UUID viewer, long sessionGeneration, long viewGeneration) {
    }
}
