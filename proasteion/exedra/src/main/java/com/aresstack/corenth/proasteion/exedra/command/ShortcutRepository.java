package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.KeyStroke;
import java.util.Map;

/**
 * SPI for loading and saving shortcut bindings.
 * Implementations may use JSON, properties files, or any other format.
 *
 * <p>Shortcuts are persisted as string representations (see
 * {@link KeyStroke#getKeyStroke(String)}) to ensure portability
 * across JVM versions and serialization stability.
 */
public interface ShortcutRepository {

    /**
     * Load all shortcut bindings as string representations.
     * Each value is a {@link KeyStroke} string (e.g. "ctrl S", "alt shift F1").
     * Returns empty map if nothing stored.
     */
    Map<String, String> loadStrings();

    /**
     * Save the full shortcut map as string representations (replaces previous state).
     */
    void saveStrings(Map<String, String> shortcuts);

    /** Load all shortcut bindings. Returns empty map if nothing stored. */
    default Map<String, KeyStroke> load() {
        Map<String, String> strings = loadStrings();
        Map<String, KeyStroke> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : strings.entrySet()) {
            KeyStroke ks = KeyStroke.getKeyStroke(e.getValue());
            if (ks != null) result.put(e.getKey(), ks);
        }
        return result;
    }

    /** Save the full shortcut map (replaces previous state). */
    default void save(Map<String, KeyStroke> shortcuts) {
        Map<String, String> strings = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, KeyStroke> e : shortcuts.entrySet()) {
            strings.put(e.getKey(), e.getValue().toString());
        }
        saveStrings(strings);
    }
}
