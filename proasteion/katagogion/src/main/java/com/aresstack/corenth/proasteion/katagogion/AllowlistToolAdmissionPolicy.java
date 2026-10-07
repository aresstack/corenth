package com.aresstack.corenth.proasteion.katagogion;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed admission by explicit allowlist of plugin ids and their permitted capabilities.
 *
 * <p>A plugin that is not listed is rejected. A listed plugin that requests a capability beyond
 * its allowance is rejected as a whole instead of being silently downgraded, so a misconfigured
 * plugin is visible at installation time.
 */
public final class AllowlistToolAdmissionPolicy implements ToolAdmissionPolicy {

    private final Map<String, Set<ToolCapability>> allowances;

    private AllowlistToolAdmissionPolicy(Map<String, Set<ToolCapability>> allowances) {
        this.allowances = Collections.unmodifiableMap(allowances);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ToolAdmission evaluate(PluginDescriptor plugin) {
        if (plugin == null) {
            return ToolAdmission.reject("no plugin descriptor");
        }
        Set<ToolCapability> allowed = allowances.get(plugin.id());
        if (allowed == null) {
            return ToolAdmission.reject("plugin '" + plugin.id() + "' is not on the tool allowlist");
        }
        if (!allowed.containsAll(plugin.requestedCapabilities())) {
            EnumSet<ToolCapability> excess = EnumSet.copyOf(plugin.requestedCapabilities());
            excess.removeAll(allowed);
            return ToolAdmission.reject("plugin '" + plugin.id() + "' requests capabilities beyond its allowance: " + excess);
        }
        return ToolAdmission.admit(plugin.requestedCapabilities(), "allowlisted");
    }

    /** Builder for {@link AllowlistToolAdmissionPolicy}. */
    public static final class Builder {
        private final Map<String, Set<ToolCapability>> allowances = new LinkedHashMap<String, Set<ToolCapability>>();

        private Builder() {
        }

        /** Allow the plugin with the given id to use at most the given capabilities. */
        public Builder allow(String pluginId, Set<ToolCapability> capabilities) {
            allowances.put(Names.requireIdentifier(pluginId, "plugin id"), ToolDescriptor.copyCapabilities(capabilities));
            return this;
        }

        public AllowlistToolAdmissionPolicy build() {
            return new AllowlistToolAdmissionPolicy(new LinkedHashMap<String, Set<ToolCapability>>(allowances));
        }
    }
}
