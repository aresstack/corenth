package com.aresstack.corenth.proasteion.exedra.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of settings categories. Categories are shown in insertion order.
 * Supports both direct category registration and provider-based dynamic registration.
 */
public final class SettingsCategoryRegistry {

    private final Map<String, SettingsCategory> categories = new LinkedHashMap<>();
    private final List<SettingsCategoryProvider> providers = new ArrayList<>();

    /** Register a category directly. Replaces any existing category with the same id. */
    public void register(SettingsCategory category) {
        if (category == null) throw new IllegalArgumentException("category must not be null");
        categories.put(category.getId(), category);
    }

    /** Register a provider that will supply categories lazily when context is available. */
    public void registerProvider(SettingsCategoryProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        providers.add(provider);
    }

    /**
     * Resolve all providers and return the full category list.
     * Direct categories come first, then provider-supplied categories in registration order.
     *
     * @param context context for providers (may be null if no providers registered)
     */
    public List<SettingsCategory> resolveAll(SettingsContext context) {
        List<SettingsCategory> result = new ArrayList<>(categories.values());
        for (SettingsCategoryProvider provider : providers) {
            List<SettingsCategory> provided = provider.getCategories(context);
            if (provided != null) {
                result.addAll(provided);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Get all directly registered categories in insertion order (without providers). */
    public List<SettingsCategory> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(categories.values()));
    }

    /** Look up a directly registered category by id. */
    public SettingsCategory findById(String id) {
        return categories.get(id);
    }

    /** Remove a category by id. */
    public void unregister(String id) {
        categories.remove(id);
    }

    /** Remove all categories and providers. */
    public void clear() {
        categories.clear();
        providers.clear();
    }
}
