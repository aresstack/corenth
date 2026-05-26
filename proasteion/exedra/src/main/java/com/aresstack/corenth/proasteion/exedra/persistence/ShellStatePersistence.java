package com.aresstack.corenth.proasteion.exedra.persistence;

import com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowDescriptor;
import com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowLayout;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages shell state persistence (window bounds, divider positions,
 * full tool-window layout).
 *
 * <p>Uses a {@link ShellStateStore} to read/write a flat key-value map.
 */
public final class ShellStatePersistence {

    private static final String KEY_X = "shell.x";
    private static final String KEY_Y = "shell.y";
    private static final String KEY_W = "shell.width";
    private static final String KEY_H = "shell.height";
    private static final String KEY_MAXIMIZED = "shell.maximized";
    private static final String PREFIX_DIVIDER = "shell.divider.";
    private static final String PREFIX_TOOL_VISIBLE = "shell.tool.visible.";
    private static final String PREFIX_TOOL_AREA = "shell.tool.area.";
    private static final String PREFIX_TOOL_INDEX = "shell.tool.index.";
    private static final String PREFIX_PANE_SELECTED = "shell.pane.selected.";

    private final ShellStateStore store;
    private Map<String, String> state;

    public ShellStatePersistence(ShellStateStore store) {
        if (store == null) throw new IllegalArgumentException("store must not be null");
        this.store = store;
        this.state = new LinkedHashMap<>(store.load());
    }

    // ---- Window bounds ----

    public void saveWindowBounds(Rectangle bounds, boolean maximized) {
        state.put(KEY_X, String.valueOf(bounds.x));
        state.put(KEY_Y, String.valueOf(bounds.y));
        state.put(KEY_W, String.valueOf(bounds.width));
        state.put(KEY_H, String.valueOf(bounds.height));
        state.put(KEY_MAXIMIZED, String.valueOf(maximized));
        flush();
    }

    public Rectangle getWindowBounds(Rectangle defaultBounds) {
        try {
            int x = Integer.parseInt(state.getOrDefault(KEY_X, String.valueOf(defaultBounds.x)));
            int y = Integer.parseInt(state.getOrDefault(KEY_Y, String.valueOf(defaultBounds.y)));
            int w = Integer.parseInt(state.getOrDefault(KEY_W, String.valueOf(defaultBounds.width)));
            int h = Integer.parseInt(state.getOrDefault(KEY_H, String.valueOf(defaultBounds.height)));
            return new Rectangle(x, y, w, h);
        } catch (NumberFormatException e) {
            return defaultBounds;
        }
    }

    public boolean isMaximized() {
        return Boolean.parseBoolean(state.getOrDefault(KEY_MAXIMIZED, "false"));
    }

    // ---- Divider positions ----

    public void saveDividerPosition(String name, int position) {
        state.put(PREFIX_DIVIDER + name, String.valueOf(position));
        flush();
    }

    public int getDividerPosition(String name, int defaultValue) {
        String val = state.get(PREFIX_DIVIDER + name);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ---- Tool window visibility ----

    public void saveToolVisibility(String toolId, boolean visible) {
        state.put(PREFIX_TOOL_VISIBLE + toolId, String.valueOf(visible));
        // No flush here — flushed as part of saveToolWindowLayout
    }

    public Boolean getToolVisibility(String toolId) {
        String val = state.get(PREFIX_TOOL_VISIBLE + toolId);
        return val != null ? Boolean.valueOf(val) : null;
    }

    /** Get all tool visibility entries. */
    public Map<String, Boolean> getAllToolVisibility() {
        Map<String, Boolean> result = new HashMap<>();
        for (Map.Entry<String, String> e : state.entrySet()) {
            if (e.getKey().startsWith(PREFIX_TOOL_VISIBLE)) {
                String id = e.getKey().substring(PREFIX_TOOL_VISIBLE.length());
                result.put(id, Boolean.valueOf(e.getValue()));
            }
        }
        return result;
    }

    // ---- Full tool-window layout ----

    /**
     * Save the full tool-window layout: area, visibility, tab index per tool,
     * and selected tab per pane.
     */
    public void saveToolWindowLayout(ToolWindowLayout layout) {
        if (layout == null) return;

        // Clear previous tool layout keys
        state.keySet().removeIf(k -> k.startsWith(PREFIX_TOOL_VISIBLE)
                || k.startsWith(PREFIX_TOOL_AREA) || k.startsWith(PREFIX_TOOL_INDEX)
                || k.startsWith(PREFIX_PANE_SELECTED));

        for (Map.Entry<String, ToolWindowLayout.ToolState> e : layout.getToolStates().entrySet()) {
            String id = e.getKey();
            ToolWindowLayout.ToolState ts = e.getValue();
            state.put(PREFIX_TOOL_VISIBLE + id, String.valueOf(ts.isVisible()));
            if (ts.getPosition() != null) {
                state.put(PREFIX_TOOL_AREA + id, ts.getPosition().name());
            }
            state.put(PREFIX_TOOL_INDEX + id, String.valueOf(ts.getTabIndex()));
        }

        for (Map.Entry<ToolWindowDescriptor.Position, Integer> se : layout.getSelectedTabs().entrySet()) {
            state.put(PREFIX_PANE_SELECTED + se.getKey().name(), String.valueOf(se.getValue()));
        }

        flush();
    }

    /**
     * Load the full tool-window layout from persisted state.
     * Returns null if no layout data exists.
     */
    public ToolWindowLayout loadToolWindowLayout() {
        Map<String, ToolWindowLayout.ToolState> tools = new LinkedHashMap<>();
        Map<ToolWindowDescriptor.Position, Integer> selectedTabs = new LinkedHashMap<>();

        boolean foundAny = false;
        for (Map.Entry<String, String> e : state.entrySet()) {
            if (e.getKey().startsWith(PREFIX_TOOL_AREA)) {
                foundAny = true;
                String id = e.getKey().substring(PREFIX_TOOL_AREA.length());
                ToolWindowDescriptor.Position pos = parsePosition(e.getValue());
                boolean visible = Boolean.parseBoolean(
                        state.getOrDefault(PREFIX_TOOL_VISIBLE + id, "true"));
                int tabIndex = parseIntSafe(state.get(PREFIX_TOOL_INDEX + id), -1);
                tools.put(id, new ToolWindowLayout.ToolState(pos, visible, tabIndex));
            }
            if (e.getKey().startsWith(PREFIX_PANE_SELECTED)) {
                String posName = e.getKey().substring(PREFIX_PANE_SELECTED.length());
                ToolWindowDescriptor.Position pos = parsePosition(posName);
                if (pos != null) {
                    selectedTabs.put(pos, parseIntSafe(e.getValue(), -1));
                }
            }
        }

        return foundAny ? new ToolWindowLayout(tools, selectedTabs) : null;
    }

    /** Flush current state to the store. */
    public void flush() {
        store.save(new LinkedHashMap<>(state));
    }

    private static ToolWindowDescriptor.Position parsePosition(String value) {
        if (value == null) return null;
        try {
            return ToolWindowDescriptor.Position.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
