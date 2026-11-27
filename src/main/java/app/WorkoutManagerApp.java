package app;

import data_access.InMemorySessionStore;
import data_access.SupabaseAuthDataAccessObject;
import data_access.SupabaseChangePasswordDataAccessObject;
import data_access.SupabaseClient;
import data_access.SupabaseFollowingDao;
import data_access.SupabaseProfileDao;
import interface_adapter.ViewManagerModel;
import interface_adapter.following.FollowingController;
import interface_adapter.following.FollowingPresenter;
import interface_adapter.following.FollowingViewModel;
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
import use_case.change_password.ChangePasswordInteractor;
import use_case.following.FollowingInteractor;
import use_case.login.LogInInteractor;
import use_case.logout.LogOutInteractor;
import use_case.register.RegisterInteractor;
import view.FollowingView;
import view.HomeView;
import view.LogInView;
import view.PlaceholderView;
import view.RegisterView;
import view.SettingsView;
import view.ViewManager;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.util.List;

/** Builds the Clean Architecture engine: creates every object and connects each one to its collaborators. */
public final class WorkoutManagerApp {
    private WorkoutManagerApp() { }

    public static void main(String[] args) {
        final SupabaseClient client;
        try {
            client = SupabaseClient.fromEnvironment();
        } catch (SupabaseClient.ConfigurationException failure) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, failure.getMessage(),
                    "Account setup is unavailable", JOptionPane.ERROR_MESSAGE));
            return;
        }
        SwingUtilities.invokeLater(() -> showApp(client));
    }

    private static void showApp(SupabaseClient client) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
            // Swing's default look and feel still works.
        }

        final InMemorySessionStore session = new InMemorySessionStore();
        final SupabaseAuthDataAccessObject auth = new SupabaseAuthDataAccessObject(client);
        final SupabaseProfileDao profiles = new SupabaseProfileDao(client);

        final ViewManagerModel viewManagerModel = new ViewManagerModel(LogInViewModel.VIEW_NAME);
        final LogInViewModel logInViewModel = new LogInViewModel();
        final RegisterViewModel registerViewModel = new RegisterViewModel();
        final HomeViewModel homeViewModel = new HomeViewModel();
        final FollowingViewModel followingViewModel = new FollowingViewModel();

        final LogInController logIn = new LogInController(new LogInInteractor(auth, profiles, session,
                new LogInPresenter(viewManagerModel, logInViewModel, homeViewModel)));
        final RegisterController register = new RegisterController(new RegisterInteractor(auth, profiles, profiles,
                session, new RegisterPresenter(viewManagerModel, registerViewModel, homeViewModel)));
        final LogOutController logOut = new LogOutController(new LogOutInteractor(session, auth,
                new LogOutPresenter(viewManagerModel, logInViewModel, homeViewModel)));
        final FollowingInteractor followingInteractor = new FollowingInteractor(session,
                new SupabaseFollowingDao(client, session), new FollowingPresenter(followingViewModel));
        final FollowingController following = new FollowingController(
                followingInteractor, followingInteractor, followingInteractor);

        // Change Password (#22)
        final SettingsViewModel settingsViewModel = new SettingsViewModel();
        final ChangePasswordController changePassword = new ChangePasswordController(new ChangePasswordInteractor(
                new SupabaseChangePasswordDataAccessObject(client, auth, session), session,
                new ChangePasswordPresenter(settingsViewModel)));

        final ViewManager views = new ViewManager(viewManagerModel);
        final LogInView logInView = new LogInView(logInViewModel, logIn, viewManagerModel);
        views.addView(LogInViewModel.VIEW_NAME, logInView);
        views.addView(RegisterViewModel.VIEW_NAME, new RegisterView(registerViewModel, register, viewManagerModel));

        // Sidebar buttons show in this order, each labelled with its screen's TITLE
        views.addSignedInView(HomeViewModel.VIEW_NAME, HomeViewModel.TITLE, new HomeView(homeViewModel, logOut));

        // Placeholders until the real plan and history views are made, then swap the view in here
        for (String title : List.of("Workout Plans", "Progress History")) {
            views.addSignedInView(title, title, new PlaceholderView(title));
        }

        views.addSignedInView(FollowingViewModel.VIEW_NAME, FollowingViewModel.TITLE,
                new FollowingView(followingViewModel, following, viewManagerModel));
        views.addSignedInView(SettingsViewModel.VIEW_NAME, SettingsViewModel.TITLE,
                new SettingsView(settingsViewModel, changePassword));

        final JFrame frame = new JFrame("Workout Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(views);
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        logInView.focusFirstField();
    }
}
