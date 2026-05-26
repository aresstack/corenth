package com.aresstack.corenth.proasteion.exedra.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of settings categories. Categories are shown in insertion order.
 */
public final class SettingsCategoryRegistry {

    private final Map<String, SettingsCategory> categories = new LinkedHashMap<>();

    /** Register a category. Replaces any existing category with the same id. */
    public void register(SettingsCategory category) {
        if (category == null) throw new IllegalArgumentException("category must not be null");
        categories.put(category.getId(), category);
    }

    /** Get all registered categories in insertion order. */
    public List<SettingsCategory> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(categories.values()));
    }

    /** Look up a category by id. */
    public SettingsCategory findById(String id) {
        return categories.get(id);
    }

    /** Remove a category by id. */
    public void unregister(String id) {
        categories.remove(id);
    }

    /** Remove all categories. */
    public void clear() {
        categories.clear();
    }
}
