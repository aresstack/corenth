package com.aresstack.corenth.proasteion.katagogion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable declaration of a tool: name, purpose, accepted arguments and the mediated
 * capabilities it needs.
 *
 * <p>The host validates invocations against this descriptor and refuses to install a tool whose
 * required capabilities were not granted to its plugin.
 */
public final class ToolDescriptor {

    private final String name;
    private final String description;
    private final List<ToolParameter> parameters;
    private final Set<ToolCapability> requiredCapabilities;

    public ToolDescriptor(String name, String description, List<ToolParameter> parameters,
                          Set<ToolCapability> requiredCapabilities) {
        this.name = Names.requireIdentifier(name, "tool name");
        this.description = Names.nullToEmpty(description);
        this.parameters = copyParameters(parameters);
        this.requiredCapabilities = copyCapabilities(requiredCapabilities);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /** Return the declared parameters in declaration order. */
    public List<ToolParameter> parameters() {
        return parameters;
    }

    /** Return the parameter with the given name, or {@code null}. */
    public ToolParameter parameter(String parameterName) {
        for (ToolParameter parameter : parameters) {
            if (parameter.name().equals(parameterName)) {
                return parameter;
            }
        }
        return null;
    }

    public Set<ToolCapability> requiredCapabilities() {
        return requiredCapabilities;
    }

    private static List<ToolParameter> copyParameters(List<ToolParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new HashSet<String>();
        List<ToolParameter> copy = new ArrayList<ToolParameter>(parameters.size());
        for (ToolParameter parameter : parameters) {
            if (parameter == null) {
                throw new IllegalArgumentException("parameters must not contain null");
            }
            if (!seen.add(parameter.name())) {
                throw new IllegalArgumentException("duplicate parameter name: " + parameter.name());
            }
            copy.add(parameter);
        }
        return Collections.unmodifiableList(copy);
    }

    static Set<ToolCapability> copyCapabilities(Set<ToolCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.noneOf(ToolCapability.class));
        }
        if (capabilities.contains(null)) {
            throw new IllegalArgumentException("capabilities must not contain null");
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    @Override
    public String toString() {
        return "ToolDescriptor{" + name + ", parameters=" + parameters.size()
                + ", requires=" + requiredCapabilities + "}";
    }
}
