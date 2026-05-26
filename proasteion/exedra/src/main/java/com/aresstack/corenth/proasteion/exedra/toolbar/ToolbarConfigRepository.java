package com.aresstack.corenth.proasteion.exedra.toolbar;

/**
 * SPI for loading and saving toolbar configuration.
 */
public interface ToolbarConfigRepository {

    /**
     * Load existing config or create a default using the factory.
     */
    ToolbarConfig load(ToolbarConfig defaultConfig);

    /**
     * Persist the given configuration.
     */
    void save(ToolbarConfig config);
}
