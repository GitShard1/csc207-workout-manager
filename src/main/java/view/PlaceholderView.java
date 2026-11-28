package view;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Empty screen that holds a sidebar spot until the real view for it is made.
 * Swap it out for the real view in WorkoutManagerApp when it's ready.
 */
public final class PlaceholderView extends JPanel {
    public PlaceholderView(String title) {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setBorder(new EmptyBorder(16, 20, 16, 20));

        add(new JLabel(title));
        add(new JLabel("Not built yet."));
    }
}
