package view;

import interface_adapter.AccountViewModel.Error;
import interface_adapter.AccountViewModel.Field;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

final class AccountForm extends JPanel {
    private static final int COLUMNS = 34;
    private final Map<Field, JTextField> inputs = new EnumMap<>(Field.class);
    private final Map<Field, JLabel> fieldErrors = new EnumMap<>(Field.class);
    private final JTextArea status = new JTextArea(3, COLUMNS);
    private final JButton submit;
    private final JButton navigate;
    private final JPasswordField password = new JPasswordField(COLUMNS);
    private final JPasswordField confirmation;

    AccountForm(boolean registering, Runnable submitAction, Runnable navigateAction) {
        super(new GridBagLayout());
        setBorder(new EmptyBorder(16, 20, 16, 20));
        final String title = registering ? "Register" : "Log In";
        setName(title);
        final GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.anchor = GridBagConstraints.NORTHWEST;
        add(new JLabel(title), row);

        addField(row, Field.EMAIL, "Email", 'E', new JTextField(COLUMNS), submitAction);
        if (registering) {
            addField(row, Field.USERNAME, "Username", 'U', new JTextField(COLUMNS), submitAction);
            addField(row, Field.DISPLAY_NAME, "Display name", 'D', new JTextField(COLUMNS), submitAction);
        }
        addField(row, Field.PASSWORD, "Password", 'P', password, submitAction);
        confirmation = registering ? new JPasswordField(COLUMNS) : null;
        if (confirmation != null) {
            addField(row, Field.CONFIRM_PASSWORD, "Confirm password", 'C', confirmation, submitAction);
        }

        status.setName("Form status");
        status.getAccessibleContext().setAccessibleName("Form status");
        status.setEditable(false);
        status.setFocusable(false);
        status.setOpaque(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setFont(new JLabel().getFont());
        add(status, row);

        submit = new JButton(title);
        submit.addActionListener(event -> submitAction.run());
        navigate = new JButton(registering ? "Back to Log In" : "Create an account");
        navigate.addActionListener(event -> navigateAction.run());
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(submit);
        actions.add(navigate);
        add(actions, row);
        row.weighty = 1;
        add(new JPanel(), row);
    }

    private void addField(GridBagConstraints row, Field field, String labelText, char mnemonic, JTextField input,
                          Runnable submitAction) {
        final JLabel label = new JLabel(labelText);
        label.setLabelFor(input);
        label.setDisplayedMnemonic(mnemonic);
        row.insets = new Insets(5, 0, 0, 0);
        add(label, row);
        row.insets = new Insets(0, 0, 0, 0);
        add(input, row);
        inputs.put(field, input);
        input.addActionListener(event -> submitAction.run());

        final JLabel error = new JLabel(" ");
        error.setName(labelText + " error");
        add(error, row);
        fieldErrors.put(field, error);
    }

    String email() {
        return inputs.get(Field.EMAIL).getText();
    }

    String username() {
        return inputs.get(Field.USERNAME).getText();
    }

    String displayName() {
        return inputs.get(Field.DISPLAY_NAME).getText();
    }

    String takePassword() {
        return takePassword(password);
    }

    String takeConfirmation() {
        return takePassword(confirmation);
    }

    private static String takePassword(JPasswordField input) {
        final char[] password = input.getPassword();
        try {
            input.setText("");
            return new String(password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    void reset() {
        inputs.values().forEach(input -> input.setText(""));
        clearMessages();
    }

    void clearMessages() {
        fieldErrors.keySet().forEach(field -> setFieldError(field, null));
        showStatus("");
    }

    void showStatus(String message) {
        status.setText(message);
        status.getAccessibleContext().setAccessibleDescription(message.isEmpty() ? null : message);
        status.setFocusable(!message.isEmpty());
    }

    Field showError(Error error) {
        final String message = "Error: " + error.message();
        if (error.field() == Field.FORM) {
            showStatus(message);
            return Field.PASSWORD;
        }
        setFieldError(error.field(), message);
        return error.field();
    }

    private void setFieldError(Field field, String message) {
        fieldErrors.get(field).setText(message == null ? " " : message);
        inputs.get(field).getAccessibleContext().setAccessibleDescription(message);
    }

    void setBusy(boolean busy) {
        submit.setEnabled(!busy);
        navigate.setEnabled(!busy);
        inputs.values().forEach(input -> input.setEnabled(!busy));
    }

    void focus(Field field) {
        if (getRootPane() != null) {
            getRootPane().setDefaultButton(submit);
        }
        inputs.get(field).requestFocusInWindow();
    }
}
