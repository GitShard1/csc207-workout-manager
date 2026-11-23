package view;

import interface_adapter.AccountViewModel.SignedIn;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

final class MyPlansView extends JPanel {
    private final JLabel account = new JLabel();
    private final JLabel handle = new JLabel();
    private final JButton logOut = new JButton("Log Out");

    MyPlansView(Runnable logOutAction) {
        setName("My Plans");
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setBorder(new EmptyBorder(16, 20, 16, 20));
        add(new JLabel("My Plans"));
        account.setName("Signed-in account");
        add(account);
        handle.setName("Signed-in handle");
        add(handle);
        add(new JLabel("Workout plans are not available in this version."));
        logOut.addActionListener(event -> logOutAction.run());
        add(logOut);
    }

    // This is the signed-in handoff; the plans flow can replace this destination without changing account actions.
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
