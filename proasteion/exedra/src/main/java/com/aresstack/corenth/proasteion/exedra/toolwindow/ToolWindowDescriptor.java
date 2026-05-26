package com.aresstack.corenth.proasteion.exedra.toolwindow;

import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * Describes a tool window that can be placed in one of the four shell areas
 * (left-top, left-bottom, right-top, right-bottom).
 */
public final class ToolWindowDescriptor {

    /** The four possible positions for a tool window. */
    public enum Position {
        LEFT_TOP,
        LEFT_BOTTOM,
        RIGHT_TOP,
        RIGHT_BOTTOM
    }

    private final String id;
    private final String title;
    private final Position defaultPosition;
    private final JComponent component;
    private final Icon icon;
    private final boolean visibleByDefault;

    public ToolWindowDescriptor(String id, String title, Position defaultPosition,
                                JComponent component, Icon icon, boolean visibleByDefault) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id must not be empty");
        if (component == null) throw new IllegalArgumentException("component must not be null");
        this.id = id;
        this.title = title != null ? title : id;
        this.defaultPosition = defaultPosition != null ? defaultPosition : Position.LEFT_TOP;
        this.component = component;
        this.icon = icon;
        this.visibleByDefault = visibleByDefault;
    }

    public ToolWindowDescriptor(String id, String title, Position defaultPosition,
                                JComponent component) {
        this(id, title, defaultPosition, component, null, true);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Position getDefaultPosition() { return defaultPosition; }
    public JComponent getComponent() { return component; }
    public Icon getIcon() { return icon; }
    public boolean isVisibleByDefault() { return visibleByDefault; }
}
