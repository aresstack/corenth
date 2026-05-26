package com.aresstack.corenth.proasteion.exedra.toolwindow;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.util.function.Supplier;

/**
 * Describes a tool window that can be placed in one of the four shell areas
 * (left-top, left-bottom, right-top, right-bottom).
 *
 * <p>Supports lazy component creation via a {@link Supplier} so that
 * heavyweight panels are only instantiated when first shown.
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
    private final Supplier<JComponent> componentFactory;
    private final Icon icon;
    private final boolean visibleByDefault;

    private JComponent component;

    /**
     * Full constructor with lazy component factory.
     */
    public ToolWindowDescriptor(String id, String title, Position defaultPosition,
                                Supplier<JComponent> componentFactory, Icon icon,
                                boolean visibleByDefault) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id must not be empty");
        if (componentFactory == null) throw new IllegalArgumentException("componentFactory must not be null");
        this.id = id;
        this.title = title != null ? title : id;
        this.defaultPosition = defaultPosition != null ? defaultPosition : Position.LEFT_TOP;
        this.componentFactory = componentFactory;
        this.icon = icon;
        this.visibleByDefault = visibleByDefault;
    }

    /** Convenience constructor with eager component. */
    public ToolWindowDescriptor(String id, String title, Position defaultPosition,
                                JComponent component, Icon icon, boolean visibleByDefault) {
        this(id, title, defaultPosition, () -> component, icon, visibleByDefault);
        if (component == null) throw new IllegalArgumentException("component must not be null");
        this.component = component;
    }

    /** Convenience constructor with eager component, visible by default. */
    public ToolWindowDescriptor(String id, String title, Position defaultPosition,
                                JComponent component) {
        this(id, title, defaultPosition, component, null, true);
    }

    /** Convenience constructor with lazy factory, visible by default. */
    public ToolWindowDescriptor(String id, String title, Position defaultPosition,
                                Supplier<JComponent> componentFactory) {
        this(id, title, defaultPosition, componentFactory, null, true);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Position getDefaultPosition() { return defaultPosition; }
    public Icon getIcon() { return icon; }
    public boolean isVisibleByDefault() { return visibleByDefault; }

    /**
     * Get or create the component. The factory is called at most once.
     */
    public JComponent getComponent() {
        if (component == null) {
            component = componentFactory.get();
        }
        return component;
    }

    /** Whether the component has been created yet. */
    public boolean isComponentCreated() {
        return component != null;
    }
}
