package com.aresstack.corenth.astu;

import java.util.Optional;

/**
 * An immutable snapshot of configuration data for a module.
 *
 * <p>This interface establishes the contract boundary for configuration in Corenth:
 * configuration is provided as an immutable snapshot to modules, not as a mutable
 * settings store or a UI-editable preferences object.
 *
 * <p>Implementations may be backed by files, environment variables, or any other source.
 * The inner model only sees the snapshot and does not depend on how it was produced.
 *
 * <p><b>Note:</b> Full typed configuration per module (indexing settings, cache policy, etc.)
 * is deferred to a follow-up issue. This interface is the minimal stable contract that
 * allows the walking skeleton to proceed.
 */
public interface ConfigSnapshot {

    /**
     * Returns the value associated with the given key, or empty if not present.
     *
     * @param key the configuration key
     * @return an Optional containing the value, or empty
     */
    Optional<String> get(String key);

    /**
     * Returns the value associated with the given key, or the provided default.
     *
     * @param key          the configuration key
     * @param defaultValue the value to return if key is absent
     * @return the configured value or the default
     */
    default String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * Returns {@code true} if a value is present for the given key.
     *
     * @param key the configuration key
     * @return true if configured
     */
    default boolean has(String key) {
        return get(key).isPresent();
    }
}
