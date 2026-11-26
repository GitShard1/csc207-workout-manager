package view;

import interface_adapter.ViewManagerModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static view.SwingTestSupport.find;
import static view.SwingTestSupport.onEdt;

/** Checks the sidebar: signed-in screens show next to it, log in fills the whole window. */
class ViewManagerTest {
    private final ViewManagerModel viewManagerModel = new ViewManagerModel("log in");

    private ViewManager views;
    private JPanel logIn;
    private JPanel home;
    private JPanel following;

    @BeforeEach
    void buildViews() throws Exception {
        onEdt(() -> {
            logIn = new JPanel();
            home = new JPanel();
            following = new JPanel();

            views = new ViewManager(viewManagerModel);
            views.addView("log in", logIn);
            views.addSignedInView("home", "Dashboard", home);
            views.addSignedInView("following", "Following", following);
        });
    }

    @Test
    void logInFillsTheWindowWithoutTheSidebar() throws Exception {
        onEdt(() -> {
            assertTrue(onScreen(logIn));
            assertFalse(onScreen(home));
            assertFalse(onScreen(sidebarButton("Dashboard")));
        });
    }

    @Test
    void signedInScreenShowsNextToTheSidebarWithItsButtonSelected() throws Exception {
        onEdt(() -> {
            viewManagerModel.setState("home");

            assertTrue(onScreen(home));
            assertTrue(onScreen(sidebarButton("Dashboard")));
            assertFalse(onScreen(logIn));
            assertTrue(sidebarButton("Dashboard").isSelected());
        });
    }

    @Test
    void clickingASidebarButtonSwitchesTheScreen() throws Exception {
        onEdt(() -> {
            viewManagerModel.setState("home");
            sidebarButton("Following").doClick(0);

            assertEquals("following", viewManagerModel.getState());
            assertTrue(onScreen(following));
            assertFalse(onScreen(home));
            assertTrue(sidebarButton("Following").isSelected());
            assertFalse(sidebarButton("Dashboard").isSelected());
        });
    }

    private JToggleButton sidebarButton(String label) {
        return find(views, JToggleButton.class, button -> label.equals(button.getText()));
    }

    // Cards hide screens with setVisible, so a screen is on screen only if it and every parent up to views are visible
    private boolean onScreen(Component component) {
        for (Component current = component; current != views; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }

        return true;
    }
}
