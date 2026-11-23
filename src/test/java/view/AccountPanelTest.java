package view;

import entity.User;
import interface_adapter.AccountViewModel;
import interface_adapter.login.LogInController;
import interface_adapter.login.LogInPresenter;
import interface_adapter.logout.LogOutController;
import interface_adapter.logout.LogOutPresenter;
import interface_adapter.register.RegisterController;
import interface_adapter.register.RegisterPresenter;
import org.junit.jupiter.api.Test;
import use_case.login.LogInAccountGateway;
import use_case.login.LogInInteractor;
import use_case.logout.LogOutInteractor;
import use_case.logout.LogOutSessionGateway;
import use_case.register.RegisterAccountGateway;
import use_case.register.RegisterInteractor;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountPanelTest {
    private static final int WAIT_SECONDS = 5;

    @Test
    void registrationErrorsKeepNonPasswordFieldsAndRetryCanOpenMyPlans() throws Exception {
        final FakeAccounts accounts = new FakeAccounts();
        final AccountPanel panel = createPanel(accounts);
        final JPanel login = named(panel, "Log In", JPanel.class);
        final JPanel register = named(panel, "Register", JPanel.class);
        final JPanel plans = named(panel, "My Plans", JPanel.class);
        final JButton submit = button(register, "Register");
        final AtomicInteger usernameErrorRenders = new AtomicInteger();
        final AtomicInteger offEdtRenders = new AtomicInteger();

        onEdt(() -> {
            assertTrue(login.isVisible());
            assertFalse(register.isVisible());
            assertFalse(plans.isVisible());
            setText(login, "Email", "unsent@example.test");
            setText(login, "Password", "unsent password");
            button(login, "Create an account").doClick(0);
            assertTrue(register.isVisible());
            assertFalse(login.isVisible());
            assertEquals("", input(login, "Email").getText());
            assertPasswordCleared(login, "Password");
            named(register, "Username error", JLabel.class).addPropertyChangeListener("text", event -> {
                usernameErrorRenders.incrementAndGet();
                if (!SwingUtilities.isEventDispatchThread()) {
                    offEdtRenders.incrementAndGet();
                }
            });
            setText(register, "Email", "sam@example.test");
            setText(register, "Username", "sam_1");
            setText(register, "Display name", "Sam");
            fillRegistrationPasswords(register);
            final JTextField password = input(register, "Password");
            final JButton back = button(register, "Back to Log In");
            final DocumentListener navigateOnClear = onRemove(password, () -> back.doClick(0));
            input(register, "Confirm password").postActionEvent();
            password.getDocument().removeDocumentListener(navigateOnClear);
            assertTrue(register.isVisible());
            assertPasswordCleared(register, "Password");
            assertPasswordCleared(register, "Confirm password");
            assertFalse(submit.isEnabled());
            return null;
        });
        awaitEdt(submit::isEnabled);
        onEdt(() -> {
            assertTrue(register.isVisible());
            assertEquals("Error: That username is taken.",
                    named(register, "Username error", JLabel.class).getText());
            assertEquals("Error: That username is taken.", fieldDescription(register, "Username"));
            assertNull(fieldDescription(register, "Email"));
            assertEquals("", status(register));
            assertEquals("sam@example.test", input(register, "Email").getText());
            assertEquals("sam_1", input(register, "Username").getText());
            assertEquals("Sam", input(register, "Display name").getText());
            assertTrue(usernameErrorRenders.get() > 0);
            assertEquals(0, offEdtRenders.get());
            return null;
        });

        accounts.registration = RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN;
        onEdt(() -> {
            setText(register, "Username", "sam_2");
            fillRegistrationPasswords(register);
            submit.doClick(0);
            return null;
        });
        awaitEdt(submit::isEnabled);
        onEdt(() -> {
            assertEquals(" ", named(register, "Username error", JLabel.class).getText());
            assertNull(fieldDescription(register, "Username"));
            assertTrue(status(register).contains("Try logging in before registering again"));
            assertAccessibleStatus(register);
            assertEquals("sam_2", input(register, "Username").getText());
            assertPasswordCleared(register, "Password");
            assertPasswordCleared(register, "Confirm password");
            return null;
        });

        accounts.registration = RegisterAccountGateway.Failure.RETRYABLE_FAILURE;
        onEdt(() -> {
            fillRegistrationPasswords(register);
            submit.doClick(0);
            return null;
        });
        awaitEdt(submit::isEnabled);
        onEdt(() -> {
            assertEquals("Error: We couldn't finish registration. Try logging in first, or try again later.",
                    status(register));
            assertAccessibleStatus(register);
            assertNull(fieldDescription(register, "Username"));
            assertEquals("sam_2", input(register, "Username").getText());
            assertPasswordCleared(register, "Password");
            assertPasswordCleared(register, "Confirm password");
            return null;
        });

        accounts.registration = RegisterAccountGateway.Failure.NO_USABLE_SESSION;
        onEdt(() -> {
            fillRegistrationPasswords(register);
            submit.doClick(0);
            return null;
        });
        awaitEdt(submit::isEnabled);
        onEdt(() -> {
            assertTrue(register.isVisible());
            assertTrue(status(register).contains("Registration didn't log you in"));
            assertTrue(status(register).contains("If asked to confirm your email"));
            setText(register, "Confirm password", "discard on navigation");
            button(register, "Back to Log In").doClick(0);
            assertTrue(login.isVisible());
            assertPasswordCleared(register, "Confirm password");
            assertEquals("", status(register));
            return null;
        });

        accounts.registration = new RegisterAccountGateway.Success(user("sam_2", "Sam"));
        onEdt(() -> {
            button(login, "Create an account").doClick(0);
            assertEquals("", input(register, "Email").getText());
            setText(register, "Email", "sam@example.test");
            setText(register, "Username", "sam_2");
            setText(register, "Display name", "Sam");
            fillRegistrationPasswords(register);
            submit.doClick(0);
            return null;
        });
        awaitEdt(plans::isVisible);
        onEdt(() -> {
            assertEquals("Signed in as Sam", named(plans, "Signed-in account", JLabel.class).getText());
            assertEquals("@sam_2", named(plans, "Signed-in handle", JLabel.class).getText());
            assertNotNull(find(plans, JLabel.class,
                    label -> "Workout plans are not available in this version.".equals(label.getText())));
            assertEquals("", input(register, "Email").getText());
            assertEquals("", input(register, "Username").getText());
            assertEquals("", input(register, "Display name").getText());
            assertPasswordCleared(register, "Password");
            assertPasswordCleared(register, "Confirm password");
            return null;
        });
        assertEquals(5, accounts.registerCalls.get());
    }

    @Test
    void loginAndLogoutSerializeActionsAndDiscardPreviousAccountStateEvenOnRevocationFailure() throws Exception {
        final FakeAccounts accounts = new FakeAccounts();
        final AccountPanel panel = createPanel(accounts);
        final JPanel login = named(panel, "Log In", JPanel.class);
        final JPanel register = named(panel, "Register", JPanel.class);
        final JPanel plans = named(panel, "My Plans", JPanel.class);
        final JButton submit = button(login, "Log In");
        final JButton navigate = button(login, "Create an account");
        final JButton logOut = button(plans, "Log Out");
        final JLabel accountLabel = named(plans, "Signed-in account", JLabel.class);
        final JLabel handleLabel = named(plans, "Signed-in handle", JLabel.class);
        final AtomicInteger offEdtRenders = new AtomicInteger();
        final Gate loginGate = new Gate();
        accounts.loginGate = loginGate;
        onEdt(() -> {
            accountLabel.addPropertyChangeListener("text", event -> {
                if (!SwingUtilities.isEventDispatchThread()) {
                    offEdtRenders.incrementAndGet();
                }
            });
            enterLogin(login, "first@example.test");
            return null;
        });
        try {
            loginGate.awaitEntered();
            onEdt(() -> {
                assertTrue(login.isVisible());
                assertEquals("Logging in…", status(login));
                assertPasswordCleared(login, "Password");
                assertFalse(submit.isEnabled());
                assertFalse(navigate.isEnabled());
                // Programmatic text actions bypass disabled buttons, so the navigation controller must guard them.
                input(login, "Password").postActionEvent();
                navigate.doClick(0);
                assertFalse(register.isVisible());
                return null;
            });
            assertEquals(1, accounts.loginCalls.get());
        } finally {
            loginGate.release();
        }
        awaitEdt(submit::isEnabled);
        onEdt(() -> {
            assertEquals("Error: Email or password is incorrect.", status(login));
            assertEquals("first@example.test", input(login, "Email").getText());
            assertPasswordCleared(login, "Password");
            return null;
        });

        accounts.loginGate = null;
        accounts.authentication = LogInAccountGateway.Failure.RETRYABLE_FAILURE;
        onEdt(() -> {
            enterLogin(login, "first@example.test");
            return null;
        });
        awaitEdt(submit::isEnabled);
        onEdt(() -> {
            assertTrue(login.isVisible());
            assertEquals("Error: We couldn't log you in right now. Please try again.", status(login));
            assertAccessibleStatus(login);
            assertPasswordCleared(login, "Password");
            return null;
        });

        final String longDisplayName = "First ".repeat(200);
        final String signedInLabel = "Signed in as " + longDisplayName;
        accounts.authentication = new LogInAccountGateway.Success(user("first", longDisplayName));
        onEdt(() -> {
            enterLogin(login, "first@example.test");
            return null;
        });
        awaitEdt(plans::isVisible);

        final Gate logoutGate = new Gate();
        accounts.logoutGate = logoutGate;
        try {
            onEdt(() -> {
                assertEquals(signedInLabel, accountLabel.getText());
                assertEquals("@first", handleLabel.getText());
                accountLabel.addPropertyChangeListener("text", event -> {
                    if (signedInLabel.equals(event.getOldValue()) && "".equals(event.getNewValue())) {
                        logOut.doClick(0);
                        assertTrue(plans.isVisible(), "Reentrant logout must not start a second transition");
                    }
                });
                logOut.doClick(0);
                assertTrue(login.isVisible());
                assertFalse(plans.isVisible());
                assertEquals("", accountLabel.getText());
                assertEquals("", handleLabel.getText());
                assertEquals("", input(login, "Email").getText());
                assertPasswordCleared(login, "Password");
                assertEquals("Signing out…", status(login));
                assertFalse(submit.isEnabled());
                assertFalse(navigate.isEnabled());
                return null;
            });
            logoutGate.awaitEntered();
            assertNull(accounts.current);
            onEdt(() -> {
                input(login, "Password").postActionEvent();
                navigate.doClick(0);
                logOut.doClick(0);
                assertFalse(register.isVisible());
                return null;
            });
            assertEquals(3, accounts.loginCalls.get());
            assertEquals(0, accounts.registerCalls.get());
            assertEquals(1, accounts.logoutCalls.get());
        } finally {
            logoutGate.release();
        }
        awaitEdt(submit::isEnabled);
        onEdt(() -> {
            assertTrue(login.isVisible());
            assertEquals("You’re signed out.", status(login));
            assertEquals("", accountLabel.getText());
            assertEquals("", handleLabel.getText());
            return null;
        });

        accounts.authentication = new LogInAccountGateway.Success(user("second", "Second"));
        onEdt(() -> {
            enterLogin(login, "second@example.test");
            return null;
        });
        awaitEdt(plans::isVisible);
        onEdt(() -> {
            assertEquals("Signed in as Second", accountLabel.getText());
            assertEquals("@second", handleLabel.getText());
            assertEquals("", status(login));
            assertEquals(0, offEdtRenders.get());
            return null;
        });
        assertEquals(4, accounts.loginCalls.get());
        assertEquals(1, accounts.logoutCalls.get());
    }

    @Test
    void reentrantDocumentAndEnabledCallbacksCannotInterruptNavigationOrLogin() throws Exception {
        final FakeAccounts accounts = new FakeAccounts();
        final AccountPanel panel = createPanel(accounts);
        final JPanel login = named(panel, "Log In", JPanel.class);
        final JPanel register = named(panel, "Register", JPanel.class);
        final JButton navigate = button(login, "Create an account");
        final JButton submit = button(login, "Log In");
        final AtomicInteger callbackCount = new AtomicInteger();
        onEdt(() -> {
            final JTextField email = input(login, "Email");
            final JTextField password = input(login, "Password");
            email.setText("first@example.test");
            password.setText(" local password ");
            final DocumentListener submitOnNavigation = onRemove(email, () -> {
                callbackCount.incrementAndGet();
                submit.doClick(0);
            });
            navigate.doClick(0);
            email.getDocument().removeDocumentListener(submitOnNavigation);
            assertTrue(register.isVisible());
            assertTrue(submit.isEnabled(), "Navigation must not have started a login worker");
            assertEquals("", status(login));
            button(register, "Back to Log In").doClick(0);

            final DocumentListener navigateOnSubmit = onRemove(password, () -> {
                callbackCount.incrementAndGet();
                navigate.doClick(0);
            });
            final JTextArea message = named(login, "Form status", JTextArea.class);
            navigate.addPropertyChangeListener("enabled", event -> {
                if (Boolean.TRUE.equals(event.getNewValue())) {
                    callbackCount.incrementAndGet();
                    assertEquals("Error: Email or password is incorrect.", message.getText());
                    navigate.doClick(0);
                }
            });
            enterLogin(login, "first@example.test");
            password.getDocument().removeDocumentListener(navigateOnSubmit);
            assertTrue(login.isVisible());
            assertEquals("Logging in…", status(login));
            return null;
        });
        awaitEdt(submit::isEnabled);
        onEdt(() -> {
            assertTrue(login.isVisible(), "Completion must finish before another transition can start");
            assertTrue(navigate.isEnabled());
            assertEquals("Error: Email or password is incorrect.", status(login));
            assertPasswordCleared(login, "Password");
            // A normal action after completion is accepted again.
            navigate.doClick(0);
            assertTrue(register.isVisible());
            return null;
        });
        assertEquals(3, callbackCount.get());
        assertEquals(1, accounts.loginCalls.get());
    }

    private static DocumentListener onRemove(JTextComponent input, Runnable callback) {
        final DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) { }

            @Override
            public void removeUpdate(DocumentEvent event) {
                callback.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) { }
        };
        input.getDocument().addDocumentListener(listener);
        return listener;
    }

    private static AccountPanel createPanel(FakeAccounts accounts) throws Exception {
        return onEdt(() -> {
            final AccountViewModel model = new AccountViewModel();
            return new AccountPanel(
                    new LogInController(new LogInInteractor(accounts, new LogInPresenter(model))),
                    new RegisterController(new RegisterInteractor(accounts, new RegisterPresenter(model))),
                    new LogOutController(new LogOutInteractor(accounts, new LogOutPresenter(model))),
                    model);
        });
    }

    private static void enterLogin(JPanel login, String email) {
        setText(login, "Email", email);
        setText(login, "Password", " local password ");
        input(login, "Password").postActionEvent();
    }

    private static void fillRegistrationPasswords(JPanel register) {
        setText(register, "Password", " local password ");
        setText(register, "Confirm password", " local password ");
    }

    private static JTextField input(JPanel form, String label) {
        final JLabel owner = find(form, JLabel.class, candidate -> label.equals(candidate.getText()));
        assertNotNull(owner, label);
        return (JTextField) owner.getLabelFor();
    }

    private static void setText(JPanel form, String label, String value) {
        input(form, label).setText(value);
    }

    private static void assertPasswordCleared(JPanel form, String label) {
        assertEquals(0, ((JPasswordField) input(form, label)).getPassword().length, label);
    }

    private static String status(JPanel form) throws Exception {
        return named(form, "Form status", JTextArea.class).getText();
    }

    private static String fieldDescription(JPanel form, String label) {
        return input(form, label).getAccessibleContext().getAccessibleDescription();
    }

    private static void assertAccessibleStatus(JPanel form) throws Exception {
        final JTextArea message = named(form, "Form status", JTextArea.class);
        assertEquals("Form status", message.getAccessibleContext().getAccessibleName());
        assertEquals(message.getText(), message.getAccessibleContext().getAccessibleDescription());
        assertTrue(message.isFocusable());
    }

    private static JButton button(JPanel form, String title) throws Exception {
        return onEdt(() -> {
            final JButton result = find(form, JButton.class, candidate -> title.equals(candidate.getText()));
            assertNotNull(result, title);
            return result;
        });
    }

    private static <T extends Component> T named(Container root, String name, Class<T> type) throws Exception {
        return onEdt(() -> {
            final T result = find(root, type, candidate -> name.equals(candidate.getName()));
            assertNotNull(result, name);
            return result;
        });
    }

    private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> matcher) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && matcher.test(type.cast(child))) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                final T match = find(nested, type, matcher);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.call();
        }
        final FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeLater(task);
        return task.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static void awaitEdt(Callable<Boolean> condition) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (!onEdt(condition)) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for Swing account completion");
            Thread.sleep(10);
        }
    }

    private static User user(String username, String displayName) {
        return new User(UUID.randomUUID(), username, displayName);
    }

    private static final class Gate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        void pause() {
            entered.countDown();
            await(released);
        }

        void awaitEntered() {
            await(entered);
        }

        void release() {
            released.countDown();
        }

        private static void await(CountDownLatch signal) {
            try {
                assertTrue(signal.await(WAIT_SECONDS, TimeUnit.SECONDS), "Timed out waiting for fake gateway");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
    }

    private static final class FakeAccounts implements LogInAccountGateway, RegisterAccountGateway,
            LogOutSessionGateway {
        private volatile LogInAccountGateway.Result authentication = LogInAccountGateway.Failure.BAD_CREDENTIALS;
        private volatile RegisterAccountGateway.Result registration = RegisterAccountGateway.Failure.USERNAME_TAKEN;
        private volatile User current;
        private volatile Gate loginGate;
        private volatile Gate logoutGate;
        private final AtomicInteger loginCalls = new AtomicInteger();
        private final AtomicInteger registerCalls = new AtomicInteger();
        private final AtomicInteger logoutCalls = new AtomicInteger();

        @Override
        public LogInAccountGateway.Result logIn(String email, String password) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals(" local password ", password);
            loginCalls.incrementAndGet();
            if (loginGate != null) {
                loginGate.pause();
            }
            assertNull(current);
            if (authentication instanceof LogInAccountGateway.Success success) {
                current = success.user();
            }
            return authentication;
        }

        @Override
        public RegisterAccountGateway.Result register(String email, String username, String name, String password) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals("sam@example.test", email);
            assertEquals("Sam", name);
            assertEquals(" local password ", password);
            registerCalls.incrementAndGet();
            assertNull(current);
            if (registration instanceof RegisterAccountGateway.Success success) {
                current = success.user();
            }
            return registration;
        }

        @Override
        public Revocation logOutCurrentSession() {
            assertFalse(SwingUtilities.isEventDispatchThread());
            logoutCalls.incrementAndGet();
            assertNotNull(current);
            current = null;
            if (logoutGate != null) {
                logoutGate.pause();
            }
            return Revocation.FAILED;
        }
    }
}
