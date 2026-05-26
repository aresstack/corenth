package com.aresstack.corenth.proasteion.exedra.persistence;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages shell state persistence (window bounds, divider positions,
 * tool-window visibility).
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
        flush();
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

    /** Flush current state to the store. */
    public void flush() {
        store.save(new LinkedHashMap<>(state));
    }
}
