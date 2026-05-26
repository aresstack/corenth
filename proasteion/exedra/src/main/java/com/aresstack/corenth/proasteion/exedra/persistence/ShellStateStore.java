package com.aresstack.corenth.proasteion.exedra.persistence;

import java.util.Map;

/**
 * SPI for saving and loading shell state (window bounds, divider positions,
 * tool-window visibility, etc.).
 */
public interface ShellStateStore {

    /** Load all persisted properties. Returns empty map if nothing is stored. */
    Map<String, String> load();

    /** Save all properties (replaces previous state). */
    void save(Map<String, String> properties);
}
