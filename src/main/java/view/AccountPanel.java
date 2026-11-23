package view;

import interface_adapter.AccountViewModel;
import interface_adapter.AccountViewModel.Error;
import interface_adapter.AccountViewModel.Field;
import interface_adapter.AccountViewModel.Outcome;
import interface_adapter.AccountViewModel.SignedIn;
import interface_adapter.AccountViewModel.SignedOut;
import interface_adapter.login.LogInController;
import interface_adapter.logout.LogOutController;
import interface_adapter.register.RegisterController;

import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.CardLayout;
import java.util.concurrent.ExecutionException;

public final class AccountPanel extends JPanel {
    private static final System.Logger LOGGER = System.getLogger(AccountPanel.class.getName());

    private final CardLayout cards = new CardLayout();
    private final AccountForm loginForm;
    private final AccountForm registerForm;
    private final HomeView home;
    private final LogInController logIn;
    private final RegisterController register;
    private final LogOutController logOut;
    private final AccountViewModel model;
    private JPanel screen;
    private boolean transitioning;
    private boolean localSignOutUnconfirmed;

    public AccountPanel(LogInController logIn, RegisterController register, LogOutController logOut,
                        AccountViewModel model) {
        this.logIn = logIn;
        this.register = register;
        this.logOut = logOut;
        this.model = model;
        setLayout(cards);
        loginForm = new AccountForm(false, this::submitLogin, this::openRegistration);
        registerForm = new AccountForm(true, this::submitRegistration, this::openLogin);
        home = new HomeView(this::signOut);
        add(loginForm, loginForm.getName());
        add(registerForm, registerForm.getName());
        add(home, home.getName());
        showScreen(loginForm);
    }

    public void focusCurrentScreen() {
        if (beginTransition(screen)) {
            try {
                focusScreen(Field.EMAIL);
            } finally {
                transitioning = false;
            }
        }
    }

    private void focusScreen(Field field) {
        if (screen instanceof AccountForm form) {
            form.focus(field);
        } else {
            home.focusLogOut();
        }
    }

    private void showScreen(JPanel next) {
        screen = next;
        cards.show(this, next.getName());
    }

    // Swing setters can call listeners synchronously. Guard from the first mutation through the committed UI.
    private boolean beginTransition(JPanel expected) {
        if (transitioning || localSignOutUnconfirmed || screen != expected) {
            return false;
        }
        transitioning = true;
        return true;
    }

    private void openRegistration() {
        navigate(loginForm, registerForm);
    }

    private void openLogin() {
        navigate(registerForm, loginForm);
    }

    private void navigate(AccountForm from, AccountForm to) {
        if (beginTransition(from)) {
            try {
                from.reset();
                showScreen(to);
                focusScreen(Field.EMAIL);
            } finally {
                transitioning = false;
            }
        }
    }

    private void submitLogin() {
        if (beginTransition(loginForm)) {
            final String email = loginForm.email();
            final String password = loginForm.takePassword();
            showBusy(loginForm, "Logging in…");
            runAction(loginForm, false, () -> logIn.logIn(email, password));
        }
    }

    private void submitRegistration() {
        if (beginTransition(registerForm)) {
            final String email = registerForm.email();
            final String username = registerForm.username();
            final String displayName = registerForm.displayName();
            final String password = registerForm.takePassword();
            final String confirmation = registerForm.takeConfirmation();
            showBusy(registerForm, "Registering…");
            runAction(registerForm, false,
                    () -> register.register(email, username, displayName, password, confirmation));
        }
    }

    private void signOut() {
        if (beginTransition(home)) {
            resetAccountScreens();
            showBusy(loginForm, "Signing out…");
            showScreen(loginForm);
            runAction(loginForm, true, logOut::logOut);
        }
    }

    private void resetAccountScreens() {
        loginForm.reset();
        registerForm.reset();
        home.clearAccount();
    }

    private static void showBusy(AccountForm form, String message) {
        form.clearMessages();
        form.showStatus(message);
        form.setBusy(true);
    }

    private void runAction(AccountForm form, boolean signingOut, Runnable action) {
        new SwingWorker<Outcome, Void>() {
            @Override
            protected Outcome doInBackground() {
                final Outcome outcome;
                try {
                    action.run();
                } finally {
                    outcome = model.takeOutcome();
                }
                return outcome;
            }

            @Override
            protected void done() {
                Outcome result = null;
                try {
                    result = get();
                    final boolean expected = signingOut ? result == SignedOut.SIGNED_OUT
                            : result instanceof SignedIn || result instanceof Error;
                    if (!expected) {
                        LOGGER.log(System.Logger.Level.ERROR, "Account action returned no matching outcome.");
                        result = null;
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(System.Logger.Level.WARNING, "Waiting for the account action was interrupted.");
                } catch (ExecutionException failure) {
                    LOGGER.log(System.Logger.Level.ERROR, "Account action failed ({0}).",
                            failure.getCause().getClass().getName());
                }
                finishAction(form, signingOut, result);
            }
        }.execute();
    }

    private void finishAction(AccountForm form, boolean signingOut, Outcome result) {
        try {
            form.clearMessages();
            Field focus = Field.EMAIL;
            if (signingOut) {
                if (result == SignedOut.SIGNED_OUT) {
                    form.showStatus("You’re signed out.");
                } else {
                    localSignOutUnconfirmed = true;
                    form.showError(new Error(Field.FORM,
                            "We couldn't confirm sign-out. Close and reopen the app before logging in again."));
                    return;
                }
            } else if (result instanceof SignedIn profile) {
                resetAccountScreens();
                home.showFor(profile);
                showScreen(home);
            } else if (result instanceof Error error) {
                focus = form.showError(error);
            } else {
                final String message = form == registerForm
                        ? "Registration was interrupted. Try logging in before registering again."
                        : "An unexpected error interrupted login. Please try again.";
                focus = form.showError(new Error(Field.FORM, message));
            }
            form.setBusy(false);
            focusScreen(focus);
        } finally {
            transitioning = false;
        }
    }
}
