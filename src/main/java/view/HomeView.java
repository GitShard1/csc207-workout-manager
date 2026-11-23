package view;

import interface_adapter.AccountViewModel.SignedIn;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

final class HomeView extends JPanel {
    private final JLabel account = new JLabel();
    private final JLabel handle = new JLabel();
    private final JButton logOut = new JButton("Log Out");

    HomeView(Runnable logOutAction) {
        setName("Home");
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setBorder(new EmptyBorder(16, 20, 16, 20));
        add(new JLabel("Home"));
        account.setName("Signed-in account");
        add(account);
        handle.setName("Signed-in handle");
        add(handle);
        add(new JLabel("Workout features aren't available yet."));
        logOut.addActionListener(event -> logOutAction.run());
        add(logOut);
    }

    void showFor(SignedIn profile) {
        account.setText("Signed in as " + profile.displayName());
        handle.setText("@" + profile.username());
    }

    void clearAccount() {
        account.setText("");
        handle.setText("");
    }

    void focusLogOut() {
        if (getRootPane() != null) {
            getRootPane().setDefaultButton(null);
        }
        logOut.requestFocusInWindow();
    }
}
