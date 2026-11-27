package view;

import interface_adapter.ViewManagerModel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.awt.Dimension;
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

        // Line down the right edge splits the sidebar from the screen next to it
        setBorder(new CompoundBorder(new MatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY), new EmptyBorder(16, 12, 16, 12)));

        final JLabel title = new JLabel("Form");
        title.setAlignmentX(CENTER_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(12));

        viewManagerModel.addPropertyChangeListener(event ->
                SwingThreads.onEdt(() -> highlight((String) event.getNewValue())));
    }

    /** Adds a button that switches to this screen. Buttons show in the order screens are added. */
    public void addScreen(String viewName, String label) {
        final JToggleButton button = new JToggleButton(label);
        button.addActionListener(event -> viewManagerModel.setState(viewName));

        // Centered, and stretched to the sidebar's width so every button lines up the same
        button.setAlignmentX(CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));

        buttonGroup.add(button);
        buttonsByViewName.put(viewName, button);
        add(button);
        add(Box.createVerticalStrut(4));
    }

    /** Whether this screen has a sidebar button, i.e. it's a signed-in screen. */
    public boolean hasScreen(String viewName) {
        return buttonsByViewName.containsKey(viewName);
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
