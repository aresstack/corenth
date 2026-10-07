package com.aresstack.corenth.proasteion.katagogion;

import java.util.List;

/**
 * Entry point of a plugin.
 *
 * <p>A plugin only describes itself and hands out its tools. It never receives a host, a global
 * context or the registry; the {@link ToolHost} decides whether and how its tools are installed.
 * Implementations may be discovered through {@code java.util.ServiceLoader} and therefore need a
 * public no-argument constructor in that case.
 */
public interface CorenthPlugin {

    /** Return the immutable plugin identity. */
    PluginDescriptor descriptor();

    /** Return the tools this plugin contributes; may be empty, never {@code null}. */
    List<Tool> tools();
}
