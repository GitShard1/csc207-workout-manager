package app;

import data_access.SupabaseAccountDao;
import data_access.SupabaseClient;
import interface_adapter.AccountViewModel;
import interface_adapter.login.LogInController;
import interface_adapter.login.LogInPresenter;
import interface_adapter.logout.LogOutController;
import interface_adapter.logout.LogOutPresenter;
import interface_adapter.register.RegisterController;
import interface_adapter.register.RegisterPresenter;
import use_case.login.LogInInteractor;
import use_case.logout.LogOutInteractor;
import use_case.register.RegisterInteractor;
import view.AccountPanel;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public final class WorkoutManagerApp {
    private WorkoutManagerApp() { }

    public static void main(String[] args) {
        final SupabaseAccountDao accounts;
        try {
            accounts = new SupabaseAccountDao(SupabaseClient.fromEnvironment());
        } catch (SupabaseClient.ConfigurationException failure) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, failure.getMessage(),
                    "Account setup is unavailable", JOptionPane.ERROR_MESSAGE));
            return;
        }
        SwingUtilities.invokeLater(() -> showApp(accounts));
    }

    private static void showApp(SupabaseAccountDao accounts) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
            // Use Swing's default look and feel if the system look and feel cannot load.
        }
        final AccountViewModel model = new AccountViewModel();
        final LogInController logIn = new LogInController(new LogInInteractor(accounts, new LogInPresenter(model)));
        final RegisterController register =
                new RegisterController(new RegisterInteractor(accounts, new RegisterPresenter(model)));
        final LogOutController logOut =
                new LogOutController(new LogOutInteractor(accounts, new LogOutPresenter(model)));
        final AccountPanel panel = new AccountPanel(logIn, register, logOut, model);
        final JFrame frame = new JFrame("Workout Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(panel);
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.focusCurrentScreen();
    }
}
