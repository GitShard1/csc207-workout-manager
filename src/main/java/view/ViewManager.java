package view;

import interface_adapter.ViewManagerModel;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.HashSet;
import java.util.Set;

/**
 * Shows whichever view the {@link ViewManagerModel} names. Each view is added under its view model's name.
 * Signed-in views share one panel with the sidebar, log in / register take the whole window.
 */
public final class ViewManager extends JPanel {
    private static final String SIGNED_IN = "signed in";

    // Cards are a generic Java Swing thing
    private final CardLayout cards = new CardLayout();
    private final CardLayout signedInCards = new CardLayout();
    private final JPanel signedInContent = new JPanel(signedInCards);
    private final JPanel signedInSidePanel = new JPanel(new BorderLayout());
    private final SidebarPanel sidebar;
    private final Set<String> signedInViewNames = new HashSet<>();

    public ViewManager(ViewManagerModel model) {
        setLayout(cards);

        // Sidebar on the left, the current signed-in screen in the middle
        sidebar = new SidebarPanel(model);
        signedInSidePanel.add(sidebar, BorderLayout.WEST);
        signedInSidePanel.add(signedInContent, BorderLayout.CENTER);

        model.addPropertyChangeListener(event ->
                SwingThreads.onEdt(() -> show((String) event.getNewValue())));
    }

    /** Adds a full-window view, like log in or register. */
    public void addView(String viewName, JPanel view) {
        view.setName(viewName);
        add(view, viewName);
    }

    /** Adds a signed-in view next to the sidebar, with a sidebar button using this label. */
    public void addSignedInView(String viewName, String sidebarLabel, JPanel view) {
        // Signed-in side panel only joins the cards with its first view, so log in (added first) still shows first
        if (signedInViewNames.isEmpty()) {
            add(signedInSidePanel, SIGNED_IN);
        }

        view.setName(viewName);
        signedInContent.add(view, viewName);
        sidebar.addScreen(viewName, sidebarLabel);
        signedInViewNames.add(viewName);
    }

    private void show(String viewName) {
        if (signedInViewNames.contains(viewName)) {
            signedInCards.show(signedInContent, viewName);
            cards.show(this, SIGNED_IN);
        } else {
            cards.show(this, viewName);
        }
    }
}
