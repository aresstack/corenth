package com.aresstack.corenth.proasteion.katagogion;

/** Immutable read-only entry describing an installed tool and the plugin that contributed it. */
public final class ToolListing {

    private final String pluginId;
    private final ToolDescriptor descriptor;

    ToolListing(String pluginId, ToolDescriptor descriptor) {
        this.pluginId = pluginId;
        this.descriptor = descriptor;
    }

    public String pluginId() {
        return pluginId;
    }

    public ToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String toString() {
        return "ToolListing{" + descriptor.name() + " from " + pluginId + "}";
    }
}
