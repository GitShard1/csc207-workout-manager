package view;

import interface_adapter.ViewManagerModel;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import java.util.HashMap;
import java.util.Map;

/**
 * Sidebar for the signed-in screens, with one toggle button per screen.
 * Buttons come from the screens the ViewManager adds, so there's no list to keep in sync here.
 */
public final class SidebarPanel extends JPanel {
    private final ViewManagerModel viewManagerModel;
    private final ButtonGroup buttonGroup = new ButtonGroup();
    private final Map<String, JToggleButton> buttonsByViewName = new HashMap<>();

    public SidebarPanel(ViewManagerModel viewManagerModel) {
        this.viewManagerModel = viewManagerModel;
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setBorder(new EmptyBorder(16, 12, 16, 12));
        add(new JLabel("Form"));

        // Highlight whichever screen is showing, even if something else switched to it (like a Back button)
        viewManagerModel.addPropertyChangeListener(event ->
                SwingThreads.onEdt(() -> highlight((String) event.getNewValue())));
    }

    /** Adds a button that switches to this screen. Buttons show in the order screens are added. */
    public void addScreen(String viewName, String label) {
        final JToggleButton button = new JToggleButton(label);
        button.addActionListener(event -> viewManagerModel.setState(viewName));

        buttonGroup.add(button);
        buttonsByViewName.put(viewName, button);
        add(button);
    }

    private void highlight(String viewName) {
        final JToggleButton button = buttonsByViewName.get(viewName);

        // Log in / register aren't in the sidebar, nothing to highlight
        if (button == null) {
            return;
        }

        button.setSelected(true);
    }
}
