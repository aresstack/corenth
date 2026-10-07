package com.aresstack.corenth.proasteion.katagogion;

import java.util.Set;

/** Immutable identity of a plugin and the mediated capabilities it requests for its tools. */
public final class PluginDescriptor {

    private final String id;
    private final String version;
    private final String displayName;
    private final Set<ToolCapability> requestedCapabilities;

    public PluginDescriptor(String id, String version, String displayName,
                            Set<ToolCapability> requestedCapabilities) {
        this.id = Names.requireIdentifier(id, "plugin id");
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("version must not be null or blank");
        }
        this.version = version;
        this.displayName = displayName == null || displayName.trim().isEmpty() ? id : displayName;
        this.requestedCapabilities = ToolDescriptor.copyCapabilities(requestedCapabilities);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public String displayName() {
        return displayName;
    }

    public Set<ToolCapability> requestedCapabilities() {
        return requestedCapabilities;
    }

    @Override
    public String toString() {
        return "PluginDescriptor{" + id + "@" + version + ", requests=" + requestedCapabilities + "}";
    }
}
