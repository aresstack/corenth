package com.aresstack.corenth.proasteion.katagogion.discovery;

import com.aresstack.corenth.proasteion.katagogion.CorenthPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of a {@link PluginDiscovery}: plugin candidates and readable problem descriptions. */
public final class DiscoveryReport {

    private final List<CorenthPlugin> plugins;
    private final List<String> problems;

    public DiscoveryReport(List<CorenthPlugin> plugins, List<String> problems) {
        this.plugins = plugins == null
                ? Collections.<CorenthPlugin>emptyList()
                : Collections.unmodifiableList(new ArrayList<CorenthPlugin>(plugins));
        this.problems = problems == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(problems));
    }

    /** Return the discovered plugins ordered by plugin id. */
    public List<CorenthPlugin> plugins() {
        return plugins;
    }

    /** Return problems such as unloadable providers or duplicate plugin ids. */
    public List<String> problems() {
        return problems;
    }
}
