package com.aresstack.corenth.proasteion.exedra.toolwindow;

import com.aresstack.corenth.proasteion.exedra.event.UiEventBus;
import com.aresstack.corenth.proasteion.exedra.event.shell.ToolWindowChangedEvent;

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
 *
 * <p>Persists full layout: area by tool id, order within pane, selected tab per pane.
 * Drag-and-drop updates are reflected in the layout model.
 *
 * <p>When an {@link UiEventBus} is set, visibility and position changes publish
 * {@link ToolWindowChangedEvent}.
 */
public final class ToolWindowRegistry {

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<ToolWindowDescriptor.Position, JTabbedPane> panes = new LinkedHashMap<>();
    private UiEventBus eventBus;

    /** Set the event bus for publishing tool-window change events. */
    public void setEventBus(UiEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /** Assign a JTabbedPane to a position. Must be called before registering tools at that position. */
    public void bindPane(ToolWindowDescriptor.Position position, JTabbedPane pane) {
        if (position == null || pane == null) throw new IllegalArgumentException("position and pane must not be null");
        panes.put(position, pane);
    }

    /** Find the position for a given JTabbedPane. Returns null if not bound. */
    public ToolWindowDescriptor.Position getPositionForPane(JTabbedPane pane) {
        for (Map.Entry<ToolWindowDescriptor.Position, JTabbedPane> e : panes.entrySet()) {
            if (e.getValue() == pane) return e.getKey();
        }
        return null;
    }

    /** Find the tool id whose component matches the given component. Returns null if not found. */
    public String findIdByComponent(java.awt.Component component) {
        if (component == null) return null;
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            if (e.getValue().descriptor.isComponentCreated()
                    && e.getValue().descriptor.getComponent() == component) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Register a tool window descriptor. Adds the tab to its pane if visible. */
    public void register(ToolWindowDescriptor descriptor) {
        if (descriptor == null) throw new IllegalArgumentException("descriptor must not be null");
        JTabbedPane pane = panes.get(descriptor.getDefaultPosition());
        boolean visible = descriptor.isVisibleByDefault();
        entries.put(descriptor.getId(), new Entry(descriptor, descriptor.getDefaultPosition(), pane, visible));

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

        // Emit event
        if (eventBus != null) {
            eventBus.publish(new ToolWindowChangedEvent(id,
                    visible ? ToolWindowChangedEvent.ChangeType.SHOWN
                            : ToolWindowChangedEvent.ChangeType.HIDDEN));
        }
    }

    /** Move a tool to a different position (updates the layout model). */
    public void moveTo(String id, ToolWindowDescriptor.Position newPosition) {
        Entry entry = entries.get(id);
        if (entry == null) return;
        JTabbedPane newPane = panes.get(newPosition);
        if (newPane == null) return;

        // Remove from current host
        JTabbedPane oldHost = findCurrentHost(entry);
        if (oldHost != null && entry.descriptor.isComponentCreated()) {
            int idx = oldHost.indexOfComponent(entry.descriptor.getComponent());
            if (idx >= 0) oldHost.removeTabAt(idx);
        }

        entry.currentPosition = newPosition;
        entry.homePane = newPane;

        if (entry.visible) {
            newPane.addTab(entry.descriptor.getTitle(), entry.descriptor.getIcon(),
                    entry.descriptor.getComponent());
        }

        // Emit event
        if (eventBus != null) {
            eventBus.publish(new ToolWindowChangedEvent(id, ToolWindowChangedEvent.ChangeType.MOVED));
        }
    }

    /**
     * Update internal position tracking after a DnD operation that already moved the tab.
     * Unlike {@link #moveTo}, this does NOT manipulate tab panes — only the model.
     */
    public void updatePositionAfterDrag(String id, ToolWindowDescriptor.Position newPosition) {
        Entry entry = entries.get(id);
        if (entry == null) return;
        JTabbedPane newPane = panes.get(newPosition);
        if (newPane == null) return;
        entry.currentPosition = newPosition;
        entry.homePane = newPane;
    }

    /** Whether a given tool window is currently visible. */
    public boolean isVisible(String id) {
        Entry entry = entries.get(id);
        return entry != null && entry.visible;
    }

    /** Get all registered descriptors (unmodifiable). */
    public List<ToolWindowDescriptor> getAll() {
        List<ToolWindowDescriptor> result = new ArrayList<>();
        for (Entry e : entries.values()) {
            result.add(e.descriptor);
        }
        return Collections.unmodifiableList(result);
    }

    /** Get the full layout state for persistence. */
    public ToolWindowLayout getLayout() {
        Map<String, ToolWindowLayout.ToolState> tools = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            JTabbedPane host = findCurrentHost(entry);
            ToolWindowDescriptor.Position pos = entry.currentPosition;
            int tabIndex = -1;
            if (host != null && entry.descriptor.isComponentCreated()) {
                tabIndex = host.indexOfComponent(entry.descriptor.getComponent());
            }
            tools.put(e.getKey(), new ToolWindowLayout.ToolState(pos, entry.visible, tabIndex));
        }

        // Selected tab per pane
        Map<ToolWindowDescriptor.Position, Integer> selectedTabs = new LinkedHashMap<>();
        for (Map.Entry<ToolWindowDescriptor.Position, JTabbedPane> pe : panes.entrySet()) {
            selectedTabs.put(pe.getKey(), pe.getValue().getSelectedIndex());
        }

        return new ToolWindowLayout(tools, selectedTabs);
    }

    /** Restore layout from persisted state. */
    public void applyLayout(ToolWindowLayout layout) {
        if (layout == null) return;

        for (Map.Entry<String, ToolWindowLayout.ToolState> e : layout.getToolStates().entrySet()) {
            String id = e.getKey();
            ToolWindowLayout.ToolState ts = e.getValue();
            Entry entry = entries.get(id);
            if (entry == null) continue;

            // Move to persisted position if different
            if (ts.getPosition() != null && ts.getPosition() != entry.currentPosition) {
                moveTo(id, ts.getPosition());
            }
            // Apply visibility
            setVisible(id, ts.isVisible());
        }

        // Restore selected tabs
        for (Map.Entry<ToolWindowDescriptor.Position, Integer> se : layout.getSelectedTabs().entrySet()) {
            JTabbedPane pane = panes.get(se.getKey());
            if (pane != null && se.getValue() >= 0 && se.getValue() < pane.getTabCount()) {
                pane.setSelectedIndex(se.getValue());
            }
        }
    }

    /** Get the visibility map (id → visible) for backward compatibility. */
    public Map<String, Boolean> getVisibilityState() {
        Map<String, Boolean> state = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            state.put(e.getKey(), e.getValue().visible);
        }
        return state;
    }

    /** Restore visibility state from persisted data (backward compatible). */
    public void applyVisibilityState(Map<String, Boolean> state) {
        if (state == null) return;
        for (Map.Entry<String, Boolean> e : state.entrySet()) {
            setVisible(e.getKey(), e.getValue());
        }
    }

    private JTabbedPane findCurrentHost(Entry entry) {
        if (!entry.descriptor.isComponentCreated()) return null;
        JComponent comp = entry.descriptor.getComponent();
        for (JTabbedPane pane : panes.values()) {
            int idx = pane.indexOfComponent(comp);
            if (idx >= 0) return pane;
        }
        return null;
    }

    private static final class Entry {
        final ToolWindowDescriptor descriptor;
        ToolWindowDescriptor.Position currentPosition;
        JTabbedPane homePane;
        boolean visible;

        Entry(ToolWindowDescriptor descriptor, ToolWindowDescriptor.Position position,
              JTabbedPane homePane, boolean visible) {
            this.descriptor = descriptor;
            this.currentPosition = position;
            this.homePane = homePane;
            this.visible = visible;
        }
    }
}
