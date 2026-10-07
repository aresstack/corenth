package com.aresstack.corenth.proasteion.katagogion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, typed result of installing one plugin into a {@link ToolHost}. */
public final class PluginInstallation {

    private final String pluginId;
    private final PluginRejectionReason rejectionReason;
    private final String message;
    private final List<String> installedTools;

    private PluginInstallation(String pluginId, PluginRejectionReason rejectionReason, String message,
                               List<String> installedTools) {
        this.pluginId = pluginId;
        this.rejectionReason = rejectionReason;
        this.message = Names.nullToEmpty(message);
        this.installedTools = installedTools;
    }

    static PluginInstallation installed(String pluginId, List<String> toolNames) {
        return new PluginInstallation(pluginId, null, "installed",
                Collections.unmodifiableList(new ArrayList<String>(toolNames)));
    }

    static PluginInstallation rejected(String pluginId, PluginRejectionReason reason, String message) {
        return new PluginInstallation(pluginId, reason, message, Collections.<String>emptyList());
    }

    public boolean isInstalled() {
        return rejectionReason == null;
    }

    /** Return the plugin id, or {@code null} when the descriptor could not be read. */
    public String pluginId() {
        return pluginId;
    }

    /** Return the rejection reason, or {@code null} when installed. */
    public PluginRejectionReason rejectionReason() {
        return rejectionReason;
    }

    public String message() {
        return message;
    }

    /** Return the names of the installed tools in plugin order; empty when rejected. */
    public List<String> installedTools() {
        return installedTools;
    }

    @Override
    public String toString() {
        return isInstalled()
                ? "PluginInstallation{" + pluginId + ", INSTALLED, tools=" + installedTools + "}"
                : "PluginInstallation{" + pluginId + ", " + rejectionReason + ", " + message + "}";
    }
}
