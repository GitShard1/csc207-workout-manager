package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import interface_adapter.login.LogInController;
import interface_adapter.login.LogInPresenter;
import interface_adapter.login.LogInViewModel;
import interface_adapter.logout.LogOutController;
import interface_adapter.logout.LogOutPresenter;
import interface_adapter.register.RegisterController;
import interface_adapter.register.RegisterPresenter;
import interface_adapter.register.RegisterViewModel;
import interface_adapter.settings.ChangePasswordController;
import interface_adapter.settings.ChangePasswordPresenter;
import interface_adapter.settings.SettingsViewModel;
import org.junit.jupiter.api.Test;
import use_case.account.AccountProfile;
import use_case.login.LogInFailure;
import use_case.login.LogInOutputData;
import use_case.register.RegisterFailure;

import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import java.awt.Container;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static view.SwingTestSupport.await;
import static view.SwingTestSupport.button;
import static view.SwingTestSupport.field;
import static view.SwingTestSupport.find;
import static view.SwingTestSupport.label;
import static view.SwingTestSupport.onEdt;

/** Drives the real views, view models and presenters, with the use cases replaced by scripted responses. */
class AccountViewsTest {
    private static final AccountProfile SAM = new AccountProfile(UUID.randomUUID(), "sam_1", "Sam");

    private final ViewManagerModel viewManagerModel = new ViewManagerModel(LogInViewModel.VIEW_NAME);
    private final LogInViewModel logInViewModel = new LogInViewModel();
    private final RegisterViewModel registerViewModel = new RegisterViewModel();
    private final HomeViewModel homeViewModel = new HomeViewModel();
    private final LogInPresenter logInPresenter = new LogInPresenter(viewManagerModel, logInViewModel, homeViewModel);
    private final RegisterPresenter registerPresenter =
            new RegisterPresenter(viewManagerModel, registerViewModel, homeViewModel);
    private final LogOutPresenter logOutPresenter =
            new LogOutPresenter(viewManagerModel, logInViewModel, homeViewModel);

    private LogInView logIn;
    private RegisterView register;
    private HomeView home;
    private SettingsView settings;
    private ViewManager views;

    @Test
    void rejectedRegistrationKeepsTheDetailsButNotThePasswords() throws Exception {
        build(request -> logInPresenter.prepareFailView(LogInFailure.RETRYABLE_FAILURE),
                request -> registerPresenter.prepareFailView(RegisterFailure.USERNAME_TAKEN));
        onEdt(() -> {
            button(logIn, "Create an account").doClick(0);
            assertTrue(register.isVisible());
            field(register, "Email").setText("sam@example.test");
            field(register, "Username").setText("sam_1");
            field(register, "Display name").setText("Sam");
            field(register, "Password").setText("long enough");
            field(register, "Confirm password").setText("long enough");
            button(register, "Register").doClick(0);
        });

        await(() -> button(register, "Register").isEnabled());

        onEdt(() -> {
            assertEquals("Error: That username is taken.", label(register, "Username error").getText());
            assertEquals("sam@example.test", field(register, "Email").getText());
            assertEquals("sam_1", field(register, "Username").getText());
            assertEquals("", field(register, "Password").getText());
            assertEquals("", field(register, "Confirm password").getText());
            button(register, "Back to Log In").doClick(0);
            assertTrue(logIn.isVisible());
        });
    }

    @Test
    void leavingSettingsThroughTheSidebarClearsThePasswordForm() throws Exception {
        build(request -> { }, request -> { });
        onEdt(() -> {
            final SettingsViewModel settingsModel = new SettingsViewModel();
            final ChangePasswordPresenter presenter = new ChangePasswordPresenter(settingsModel);
            settings = new SettingsView(settingsModel, new ChangePasswordController(
                    request -> presenter.prepareFailView("Incorrect current password.")));
            views.addSignedInView(SettingsViewModel.VIEW_NAME, SettingsViewModel.TITLE, settings);

            viewManagerModel.setState(HomeViewModel.VIEW_NAME);
            find(views, JToggleButton.class, button -> SettingsViewModel.TITLE.equals(button.getText())).doClick(0);
            assertTrue(settings.isVisible());
            field(settings, "Current password").setText("old password");
            field(settings, "New password").setText("new password");
            field(settings, "Confirm new password").setText("new password");
            button(settings, "Change Password").doClick(0);
        });

        await(() -> button(settings, "Change Password").isEnabled());
        onEdt(() -> {
            assertEquals("Error: Incorrect current password.", status(settings));
            find(views, JToggleButton.class, button -> HomeViewModel.TITLE.equals(button.getText())).doClick(0);
            assertTrue(home.isVisible());
        });

        await(() -> status(settings).isEmpty());
        onEdt(() -> {
            assertEquals("", field(settings, "Current password").getText());
            assertEquals("", field(settings, "New password").getText());
            assertEquals("", field(settings, "Confirm new password").getText());
        });
    }

    @Test
    void logInOpensHomeAndLogOutReturnsToAClearedLogInForm() throws Exception {
        build(request -> logInPresenter.prepareSuccessView(new LogInOutputData(SAM)), request -> { });
        onEdt(() -> {
            field(logIn, "Email").setText("sam@example.test");
            field(logIn, "Password").setText("long enough");
            button(logIn, "Log In").doClick(0);
        });

        await(home::isVisible);
        await(() -> field(logIn, "Email").getText().isEmpty());

        onEdt(() -> {
            assertEquals("Signed in as Sam", label(home, "Signed in as").getText());
            assertEquals("@sam_1", label(home, "Handle").getText());
            button(home, "Log Out").doClick(0);
        });

        await(logIn::isVisible);
        await(() -> status(logIn).equals("You’re signed out."));
        onEdt(() -> assertEquals("", label(home, "Signed in as").getText()));
    }

    @Test
    void unexpectedFailureReEnablesTheFormWithAnError() throws Exception {
        build(request -> {
            throw new IllegalStateException("fixture failure");
        }, request -> { });
        onEdt(() -> {
            field(logIn, "Email").setText("sam@example.test");
            field(logIn, "Password").setText("long enough");
            button(logIn, "Log In").doClick(0);
        });

        await(() -> button(logIn, "Log In").isEnabled());

        onEdt(() -> {
            assertTrue(logIn.isVisible());
            assertTrue(status(logIn).startsWith("Error: "));
        });
    }

    private void build(use_case.login.LogInInputBoundary logInUseCase,
                       use_case.register.RegisterInputBoundary registerUseCase) throws Exception {
        onEdt(() -> {
            logIn = new LogInView(logInViewModel, new LogInController(logInUseCase), viewManagerModel);
            register = new RegisterView(registerViewModel, new RegisterController(registerUseCase), viewManagerModel);
            home = new HomeView(homeViewModel, new LogOutController(logOutPresenter::prepareSuccessView));
            views = new ViewManager(viewManagerModel);
            views.addView(LogInViewModel.VIEW_NAME, logIn);
            views.addView(RegisterViewModel.VIEW_NAME, register);
            views.addSignedInView(HomeViewModel.VIEW_NAME, HomeViewModel.TITLE, home);
        });
    }

    private static String status(Container form) {
        return find(form, JTextArea.class, area -> "Form status".equals(area.getName())).getText();
    }
}
