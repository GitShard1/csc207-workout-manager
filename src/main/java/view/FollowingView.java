package view;

import interface_adapter.ViewManagerModel;
import interface_adapter.following.FollowingController;
import interface_adapter.following.FollowingState;
import interface_adapter.following.FollowingViewModel;
import interface_adapter.home.HomeViewModel;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public final class FollowingView extends JPanel {
    private final FollowingViewModel viewModel;
    private final FollowingController controller;
    private final JTextField username = new JTextField(16);
    private final JButton search = new JButton("Search");
    private final JLabel result = new JLabel();
    private final JButton follow = new JButton("Follow");
    private final JLabel status = new JLabel(" ");
    private final JList<String> accounts = new JList<>();
    private final JButton refresh = new JButton("Refresh");
    private final JButton back = new JButton("Back to Home");

    public FollowingView(FollowingViewModel viewModel, FollowingController controller, ViewManagerModel viewManager) {
        super(new BorderLayout(0, 8));
        this.viewModel = viewModel;
        this.controller = controller;
        setBorder(new EmptyBorder(16, 20, 16, 20));

        final JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.PAGE_AXIS));
        final JLabel usernameLabel = new JLabel("Username");
        usernameLabel.setLabelFor(username);
        final JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        searchRow.add(usernameLabel);
        searchRow.add(username);
        searchRow.add(search);
        form.add(searchRow);
        final JPanel resultRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        resultRow.add(follow);
        result.setName("Account result");
        resultRow.add(result);
        form.add(resultRow);
        status.setName("Following status");
        form.add(status);
        final JLabel listLabel = new JLabel("Following");
        listLabel.setLabelFor(accounts);
        form.add(listLabel);
        add(form, BorderLayout.NORTH);

        accounts.setVisibleRowCount(5);
        accounts.setPrototypeCellValue("@username  -  Display name");
        add(new JScrollPane(accounts), BorderLayout.CENTER);
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(back);
        actions.add(refresh);
        add(actions, BorderLayout.SOUTH);

        username.addActionListener(event -> search.doClick(0));
        search.addActionListener(event -> {
            final String query = username.getText();
            run("Searching...", () -> controller.findAccount(query));
        });
        follow.addActionListener(event -> {
            final FollowingState.Item account = viewModel.getState().result();
            if (account != null) {
                run("Saving follow...", () -> controller.follow(account.accountId()));
            }
        });
        refresh.addActionListener(event -> loadFollowing());
        back.addActionListener(event -> viewManager.setState(HomeViewModel.VIEW_NAME));
        viewModel.addPropertyChangeListener(event ->
                SwingThreads.onEdt(() -> render((FollowingState) event.getNewValue())));

        // Clearing on every way out, not just Back, keeps one account's bookmarks and search away from the next
        viewManager.addPropertyChangeListener(event -> SwingThreads.onEdt(() -> {
            if (viewModel.getViewName().equals(event.getNewValue())) {
                loadFollowing();
            } else {
                clear();
            }
        }));
        render(viewModel.getState());
        setBusy(false);
    }

    private void loadFollowing() {
        run("Loading Following...", controller::loadFollowing);
    }

    private void clear() {
        viewModel.setState(FollowingState.empty());
        username.setText("");
    }

    private void run(String message, Runnable action) {
        status.setText(message);
        setBusy(true);
        SwingThreads.inBackground(action, () -> status.setText("Something went wrong. Try again."),
                () -> setBusy(false));
    }

    private void render(FollowingState state) {
        final FollowingState.Item account = state.result();
        result.setText(account == null ? "" : account.label());
        follow.setVisible(account != null);
        accounts.setListData(state.accounts().stream().map(FollowingState.Item::label).toArray(String[]::new));
        status.setText(state.message().isEmpty() ? " " : state.message());
    }

    private void setBusy(boolean busy) {
        username.setEnabled(!busy);
        search.setEnabled(!busy);
        follow.setEnabled(!busy);
        refresh.setEnabled(!busy);
        back.setEnabled(!busy);
    }
}
