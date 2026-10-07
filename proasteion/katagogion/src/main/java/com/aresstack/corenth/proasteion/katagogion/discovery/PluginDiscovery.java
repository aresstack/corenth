package com.aresstack.corenth.proasteion.katagogion.discovery;

/**
 * Finds plugin candidates without installing them.
 *
 * <p>Discovery only locates {@code CorenthPlugin} implementations; installing them stays an explicit
 * {@code ToolHost.install} call that is subject to the admission policy.
 */
public interface PluginDiscovery {

    /**
     * Locate plugin candidates.
     *
     * @return the discovered plugins and the problems met; never {@code null}
     */
    DiscoveryReport discover();
}
