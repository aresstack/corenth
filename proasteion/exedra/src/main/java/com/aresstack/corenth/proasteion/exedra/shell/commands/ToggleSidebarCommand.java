package com.aresstack.corenth.proasteion.exedra.shell.commands;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;

import javax.swing.JSplitPane;

/**
 * Toggles visibility of a sidebar (left or right split pane).
 * Hides by setting divider to 0 (left) or max (right); restores previous position.
 */
public final class ToggleSidebarCommand implements ShellCommand {

    public enum Side { LEFT, RIGHT }

    private final String id;
    private final String label;
    private final JSplitPane splitPane;
    private final Side side;
    private int savedDividerLocation = -1;

    public ToggleSidebarCommand(String id, String label, JSplitPane splitPane, Side side) {
        this.id = id;
        this.label = label;
        this.splitPane = splitPane;
        this.side = side;
    }

    @Override public String getId() { return id; }
    @Override public String getLabel() { return label; }

    @Override
    public void perform() {
        int current = splitPane.getDividerLocation();
        if (side == Side.LEFT) {
            if (current <= 1) {
                // Show
                splitPane.setDividerLocation(savedDividerLocation > 0 ? savedDividerLocation : 200);
            } else {
                // Hide
                savedDividerLocation = current;
                splitPane.setDividerLocation(0);
            }
        } else {
            int max = splitPane.getWidth() - splitPane.getDividerSize();
            if (current >= max - 1) {
                // Show
                splitPane.setDividerLocation(savedDividerLocation > 0 ? savedDividerLocation : max - 200);
            } else {
                // Hide
                savedDividerLocation = current;
                splitPane.setDividerLocation(max);
            }
        }
    }
}
