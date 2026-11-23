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
    private final CardLayout cards = new CardLayout();
    private final AccountForm loginForm;
    private final AccountForm registerForm;
    private final MyPlansView myPlans;
    private final LogInController logIn;
    private final RegisterController register;
    private final LogOutController logOut;
    private final AccountViewModel model;
    private JPanel screen;
    private boolean transitioning;

    public AccountPanel(LogInController logIn, RegisterController register, LogOutController logOut,
                        AccountViewModel model) {
        this.logIn = logIn;
        this.register = register;
        this.logOut = logOut;
        this.model = model;
        setLayout(cards);
        loginForm = new AccountForm(false, this::submitLogin, this::openRegistration);
        registerForm = new AccountForm(true, this::submitRegistration, this::openLogin);
        myPlans = new MyPlansView(this::leaveMyPlans);
        add(loginForm, loginForm.getName());
        add(registerForm, registerForm.getName());
        add(myPlans, myPlans.getName());
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
            myPlans.focusLogOut();
        }
    }

    private void showScreen(JPanel next) {
        screen = next;
        cards.show(this, next.getName());
    }

    // Swing setters can call listeners synchronously. Guard from the first mutation through the committed UI.
    private boolean beginTransition(JPanel expected) {
        if (transitioning || screen != expected) {
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
            final String email = loginForm.text(Field.EMAIL);
            final String password = loginForm.takePassword(Field.PASSWORD);
            showBusy(loginForm, "Logging in…");
            runAction(loginForm, false, () -> logIn.logIn(email, password));
        }
    }

    private void submitRegistration() {
        if (beginTransition(registerForm)) {
            final String email = registerForm.text(Field.EMAIL);
            final String username = registerForm.text(Field.USERNAME);
            final String displayName = registerForm.text(Field.DISPLAY_NAME);
            final String password = registerForm.takePassword(Field.PASSWORD);
            final String confirmation = registerForm.takePassword(Field.CONFIRM_PASSWORD);
            showBusy(registerForm, "Registering…");
            runAction(registerForm, false,
                    () -> register.register(email, username, displayName, password, confirmation));
        }
    }

    private void leaveMyPlans() {
        if (beginTransition(myPlans)) {
            resetAccountScreens();
            showBusy(loginForm, "Signing out…");
            showScreen(loginForm);
            runAction(loginForm, true, logOut::logOut);
        }
    }

    private void resetAccountScreens() {
        loginForm.reset();
        registerForm.reset();
        myPlans.clearAccount();
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
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ignored) {
                    // Do not expose internal errors or request data in the UI.
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
                // The Login card is already visible; no logout result can restore the previous account.
                if (result == SignedOut.SIGNED_OUT) {
                    form.showStatus("You’re signed out.");
                }
            } else if (result instanceof SignedIn profile) {
                resetAccountScreens();
                myPlans.showFor(profile);
                showScreen(myPlans);
            } else if (result instanceof Error error) {
                focus = form.showError(error);
            } else {
                final String message = form == registerForm
                        ? "We couldn't confirm registration. Try logging in before registering again."
                        : "We couldn't log you in right now. Please try again.";
                focus = form.showError(new Error(Field.FORM, message));
            }
            form.setBusy(false);
            focusScreen(focus);
        } finally {
            transitioning = false;
        }
    }
}
