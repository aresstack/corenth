package com.aresstack.corenth.proasteion.exedra.toolwindow;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of tool windows that can be shown/hidden via the View menu.
 * Each tool window has a stable id, a default position (one of the four panes),
 * and visibility state that can be toggled.
 */
public final class ToolWindowRegistry {

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<ToolWindowDescriptor.Position, JTabbedPane> panes = new LinkedHashMap<>();

    /** Assign a JTabbedPane to a position. Must be called before registering tools at that position. */
    public void bindPane(ToolWindowDescriptor.Position position, JTabbedPane pane) {
        if (position == null || pane == null) throw new IllegalArgumentException("position and pane must not be null");
        panes.put(position, pane);
    }

    /** Register a tool window descriptor. Adds the tab to its pane if visible. */
    public void register(ToolWindowDescriptor descriptor) {
        if (descriptor == null) throw new IllegalArgumentException("descriptor must not be null");
        JTabbedPane pane = panes.get(descriptor.getDefaultPosition());
        boolean visible = descriptor.isVisibleByDefault();
        entries.put(descriptor.getId(), new Entry(descriptor, pane, visible));

        if (visible && pane != null) {
            pane.addTab(descriptor.getTitle(), descriptor.getIcon(), descriptor.getComponent());
        }
    }

    /** Toggle visibility of a tool window. */
    public void setVisible(String id, boolean visible) {
        Entry entry = entries.get(id);
        if (entry == null) return;
        if (entry.visible == visible) return;

        entry.visible = visible;
        JTabbedPane host = findCurrentHost(entry);
        if (host == null) host = entry.homePane;

        if (visible) {
            if (host != null) {
                host.addTab(entry.descriptor.getTitle(), entry.descriptor.getIcon(),
                        entry.descriptor.getComponent());
            }
        } else {
            if (host != null) {
                int idx = host.indexOfComponent(entry.descriptor.getComponent());
                if (idx >= 0) host.removeTabAt(idx);
            }
        }
    }

    /** Whether a given tool window is currently visible. */
    public boolean isVisible(String id) {
        Entry entry = entries.get(id);
        return entry != null && entry.visible;
    }

    /** Get all registered entries (unmodifiable). */
    public List<ToolWindowDescriptor> getAll() {
        List<ToolWindowDescriptor> result = new ArrayList<>();
        for (Entry e : entries.values()) {
            result.add(e.descriptor);
        }
        return Collections.unmodifiableList(result);
    }

    /** Get the visibility map (id → visible) for persistence. */
    public Map<String, Boolean> getVisibilityState() {
        Map<String, Boolean> state = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            state.put(e.getKey(), e.getValue().visible);
        }
        return state;
    }

    /** Restore visibility state from persisted data. */
    public void applyVisibilityState(Map<String, Boolean> state) {
        if (state == null) return;
        for (Map.Entry<String, Boolean> e : state.entrySet()) {
            setVisible(e.getKey(), e.getValue());
        }
    }

    private JTabbedPane findCurrentHost(Entry entry) {
        JComponent comp = entry.descriptor.getComponent();
        for (JTabbedPane pane : panes.values()) {
            int idx = pane.indexOfComponent(comp);
            if (idx >= 0) return pane;
        }
        return null;
    }

    private static final class Entry {
        final ToolWindowDescriptor descriptor;
        final JTabbedPane homePane;
        boolean visible;

        Entry(ToolWindowDescriptor descriptor, JTabbedPane homePane, boolean visible) {
            this.descriptor = descriptor;
            this.homePane = homePane;
            this.visible = visible;
        }
    }
}
