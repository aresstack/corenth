package com.aresstack.corenth.proasteion.katagogion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless host that installs plugins explicitly and invokes their tools.
 *
 * <p>Installation is all-or-nothing per plugin: the admission policy decides first, then every
 * tool must require only capabilities that were requested, granted and implemented, and every tool
 * name must be free. Invocations are validated against the tool descriptor before the tool runs.
 *
 * <p>The host converts unexpected runtime exceptions of a tool into a failed result so that one
 * faulty tool cannot break the caller. This is error containment, not isolation: plugin code runs
 * in the same JVM with the host's permissions, and {@link Error}s are not caught.
 */
public final class ToolHost {

    private final ToolAdmissionPolicy admissionPolicy;
    private final ToolCapabilities capabilities;
    private final ToolRegistry registry = new ToolRegistry();
    private final Set<String> installedPlugins = new HashSet<String>();

    public ToolHost(ToolAdmissionPolicy admissionPolicy, ToolCapabilities capabilities) {
        if (admissionPolicy == null) {
            throw new IllegalArgumentException("admissionPolicy must not be null");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities must not be null");
        }
        this.admissionPolicy = admissionPolicy;
        this.capabilities = capabilities;
    }

    /**
     * Install a plugin and register all of its tools, or none of them.
     *
     * @param plugin the plugin to install
     * @return the typed installation result; never {@code null}
     */
    public synchronized PluginInstallation install(CorenthPlugin plugin) {
        if (plugin == null) {
            return PluginInstallation.rejected(null, PluginRejectionReason.INVALID_PLUGIN, "plugin must not be null");
        }
        PluginDescriptor descriptor;
        try {
            descriptor = plugin.descriptor();
        } catch (RuntimeException e) {
            return PluginInstallation.rejected(null, PluginRejectionReason.INVALID_PLUGIN,
                    "plugin descriptor failed: " + e.getClass().getSimpleName());
        }
        if (descriptor == null) {
            return PluginInstallation.rejected(null, PluginRejectionReason.INVALID_PLUGIN, "plugin descriptor is null");
        }
        String pluginId = descriptor.id();
        if (installedPlugins.contains(pluginId)) {
            return PluginInstallation.rejected(pluginId, PluginRejectionReason.DUPLICATE_PLUGIN,
                    "plugin already installed: " + pluginId);
        }

        ToolAdmission admission = admissionPolicy.evaluate(descriptor);
        if (admission == null || !admission.isAdmitted()) {
            return PluginInstallation.rejected(pluginId, PluginRejectionReason.NOT_ADMITTED,
                    admission == null ? "admission policy returned no decision" : admission.reason());
        }
        Set<ToolCapability> granted = EnumSet.copyOf(withNone(admission.grantedCapabilities()));
        granted.retainAll(descriptor.requestedCapabilities());

        List<ToolCandidate> candidates;
        try {
            candidates = readTools(plugin);
        } catch (InvalidPluginException e) {
            return PluginInstallation.rejected(pluginId, PluginRejectionReason.INVALID_PLUGIN, e.getMessage());
        }

        Set<String> names = new HashSet<String>();
        for (ToolCandidate candidate : candidates) {
            ToolDescriptor toolDescriptor = candidate.descriptor;
            if (!granted.containsAll(toolDescriptor.requiredCapabilities())) {
                return PluginInstallation.rejected(pluginId, PluginRejectionReason.CAPABILITY_NOT_GRANTED,
                        "tool '" + toolDescriptor.name() + "' requires " + toolDescriptor.requiredCapabilities()
                                + " but plugin was granted " + granted);
            }
            if (!capabilities.available().containsAll(toolDescriptor.requiredCapabilities())) {
                return PluginInstallation.rejected(pluginId, PluginRejectionReason.CAPABILITY_UNAVAILABLE,
                        "tool '" + toolDescriptor.name() + "' requires " + toolDescriptor.requiredCapabilities()
                                + " but this host offers " + capabilities.available());
            }
            if (!names.add(toolDescriptor.name()) || registry.contains(toolDescriptor.name())) {
                return PluginInstallation.rejected(pluginId, PluginRejectionReason.DUPLICATE_TOOL,
                        "tool name already taken: " + toolDescriptor.name());
            }
        }

        Set<ToolCapability> effective = EnumSet.copyOf(withNone(granted));
        effective.retainAll(capabilities.available());
        ToolContext context = ToolContext.granting(capabilities, effective);
        List<String> installedTools = new ArrayList<String>(candidates.size());
        for (ToolCandidate candidate : candidates) {
            registry.register(pluginId, candidate.tool, candidate.descriptor, context);
            installedTools.add(candidate.descriptor.name());
        }
        installedPlugins.add(pluginId);
        return PluginInstallation.installed(pluginId, installedTools);
    }

    /** Return the installed tools sorted by name. */
    public synchronized List<ToolListing> tools() {
        return registry.listings();
    }

    /**
     * Validate and run a tool invocation.
     *
     * @param invocation the invocation
     * @return the typed result; never {@code null}
     */
    public ToolResult invoke(ToolInvocation invocation) {
        if (invocation == null) {
            return ToolResult.failure(ToolFailureReason.INVALID_ARGUMENTS, "invocation must not be null");
        }
        ToolRegistry.Entry entry;
        synchronized (this) {
            entry = registry.find(invocation.toolName());
        }
        if (entry == null) {
            return ToolResult.failure(ToolFailureReason.UNKNOWN_TOOL, "unknown tool: " + invocation.toolName());
        }
        String argumentProblem = validateArguments(entry.descriptor, invocation.arguments());
        if (argumentProblem != null) {
            return ToolResult.failure(ToolFailureReason.INVALID_ARGUMENTS, argumentProblem);
        }
        try {
            ToolResult result = entry.tool.execute(invocation, entry.context);
            if (result == null) {
                return ToolResult.failure(ToolFailureReason.EXECUTION_FAILED,
                        "tool '" + invocation.toolName() + "' returned no result");
            }
            return result;
        } catch (CapabilityNotGrantedException e) {
            return ToolResult.failure(ToolFailureReason.CAPABILITY_NOT_GRANTED, e.getMessage());
        } catch (RuntimeException e) {
            // Report the exception type only; messages of foreign code may carry unvetted content.
            return ToolResult.failure(ToolFailureReason.EXECUTION_FAILED,
                    "tool '" + invocation.toolName() + "' failed: " + e.getClass().getName());
        }
    }

    private static String validateArguments(ToolDescriptor descriptor, Map<String, String> arguments) {
        for (ToolParameter parameter : descriptor.parameters()) {
            if (parameter.isRequired()) {
                String value = arguments.get(parameter.name());
                if (value == null || value.trim().isEmpty()) {
                    return "missing required argument: " + parameter.name();
                }
            }
        }
        for (String name : arguments.keySet()) {
            if (descriptor.parameter(name) == null) {
                return "undeclared argument: " + name;
            }
        }
        return null;
    }

    private static List<ToolCandidate> readTools(CorenthPlugin plugin) throws InvalidPluginException {
        List<Tool> tools;
        try {
            tools = plugin.tools();
        } catch (RuntimeException e) {
            throw new InvalidPluginException("plugin tools failed: " + e.getClass().getSimpleName());
        }
        if (tools == null) {
            throw new InvalidPluginException("plugin returned no tool list");
        }
        List<ToolCandidate> candidates = new ArrayList<ToolCandidate>(tools.size());
        for (Tool tool : tools) {
            if (tool == null) {
                throw new InvalidPluginException("plugin returned a null tool");
            }
            ToolDescriptor toolDescriptor;
            try {
                toolDescriptor = tool.descriptor();
            } catch (RuntimeException e) {
                throw new InvalidPluginException("tool descriptor failed: " + e.getClass().getSimpleName());
            }
            if (toolDescriptor == null) {
                throw new InvalidPluginException("tool returned a null descriptor");
            }
            candidates.add(new ToolCandidate(tool, toolDescriptor));
        }
        return candidates;
    }

    private static Set<ToolCapability> withNone(Set<ToolCapability> capabilities) {
        return capabilities.isEmpty() ? EnumSet.noneOf(ToolCapability.class) : capabilities;
    }

    /** A tool with the descriptor captured once, so later descriptor changes have no effect. */
    private static final class ToolCandidate {
        final Tool tool;
        final ToolDescriptor descriptor;

        ToolCandidate(Tool tool, ToolDescriptor descriptor) {
            this.tool = tool;
            this.descriptor = descriptor;
        }
    }

    /** Signal an unreadable or malformed plugin during installation. */
    private static final class InvalidPluginException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidPluginException(String message) {
            super(message);
        }
    }
}
