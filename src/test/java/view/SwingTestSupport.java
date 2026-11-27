package view;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

final class SwingTestSupport {
    private SwingTestSupport() { }

    static JTextField field(Container form, String labelText) {
        return (JTextField) find(form, JLabel.class, label -> labelText.equals(label.getText())).getLabelFor();
    }

    static JLabel label(Container container, String name) {
        return find(container, JLabel.class, label -> name.equals(label.getName()));
    }

    static JButton button(Container container, String text) {
        return find(container, JButton.class, button -> text.equals(button.getText()));
    }

    static <T extends JComponent> T find(Container container, Class<T> type, Predicate<T> match) {
        final T found = findOrNull(container, type, match);
        if (found == null) {
            throw new AssertionError("No matching " + type.getSimpleName());
        }
        return found;
    }

    private static <T extends JComponent> T findOrNull(Container container, Class<T> type, Predicate<T> match) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child) && match.test(type.cast(child))) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                final T found = findOrNull(nested, type, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static void onEdt(Runnable action) throws Exception {
        final FutureTask<Void> task = new FutureTask<>(action, null);
        SwingUtilities.invokeLater(task);
        task.get(5, TimeUnit.SECONDS);
    }

    static void await(Callable<Boolean> condition) throws Exception {
        final CompletableFuture<Void> satisfied = new CompletableFuture<>();
        final Timer checks = new Timer(10, event -> {
            try {
                if (condition.call()) {
                    satisfied.complete(null);
                }
            } catch (Exception | AssertionError failure) {
                satisfied.completeExceptionally(failure);
            }
        });
        checks.setInitialDelay(0);
        onEdt(checks::start);
        try {
            satisfied.get(5, TimeUnit.SECONDS);
        } finally {
            onEdt(checks::stop);
        }
    }
}
