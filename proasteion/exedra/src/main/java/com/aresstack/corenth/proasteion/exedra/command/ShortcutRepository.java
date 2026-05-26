package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.KeyStroke;
import java.util.Map;

/**
 * SPI for loading and saving shortcut bindings.
 * Implementations may use JSON, properties files, or any other format.
 */
public interface ShortcutRepository {

    /** Load all shortcut bindings. Returns empty map if nothing stored. */
    Map<String, KeyStroke> load();

    /** Save the full shortcut map (replaces previous state). */
    void save(Map<String, KeyStroke> shortcuts);
}
