package com.aresstack.corenth.proasteion.exedra.settings;

import java.util.List;

/**
 * SPI for providing settings categories dynamically.
 * Implementations can use the {@link SettingsContext} to access shell services
 * when building their category panels.
 */
public interface SettingsCategoryProvider {

    /**
     * Create settings categories for this provider.
     *
     * @param context access to shell services for building category panels
     * @return list of categories to register (may be empty, never null)
     */
    List<SettingsCategory> getCategories(SettingsContext context);
}
