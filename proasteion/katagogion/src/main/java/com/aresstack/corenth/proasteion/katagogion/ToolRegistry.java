package com.aresstack.corenth.proasteion.katagogion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Explicit, instance-scoped registry of installed tools keyed by unique tool name.
 *
 * <p>The registry is owned by a {@link ToolHost}; it is not static and never handed to plugins.
 * Each entry binds a tool to its plugin and to the context built from that plugin's grants.
 */
final class ToolRegistry {

    private final Map<String, Entry> entries = new TreeMap<String, Entry>();

    boolean contains(String toolName) {
        return entries.containsKey(toolName);
    }

    void register(String pluginId, Tool tool, ToolDescriptor descriptor, ToolContext context) {
        if (entries.containsKey(descriptor.name())) {
            throw new IllegalStateException("tool already registered: " + descriptor.name());
        }
        entries.put(descriptor.name(), new Entry(pluginId, tool, descriptor, context));
    }

    Entry find(String toolName) {
        return entries.get(toolName);
    }

    /** Return the listings sorted by tool name. */
    List<ToolListing> listings() {
        List<ToolListing> listings = new ArrayList<ToolListing>(entries.size());
        for (Entry entry : entries.values()) {
            listings.add(new ToolListing(entry.pluginId, entry.descriptor));
        }
        return Collections.unmodifiableList(listings);
    }

    /** A registered tool with the descriptor captured at installation time. */
    static final class Entry {
        final String pluginId;
        final Tool tool;
        final ToolDescriptor descriptor;
        final ToolContext context;

        private Entry(String pluginId, Tool tool, ToolDescriptor descriptor, ToolContext context) {
            this.pluginId = pluginId;
            this.tool = tool;
            this.descriptor = descriptor;
            this.context = context;
        }
    }
}
