package com.aresstack.corenth.proasteion.katagogion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable request to run one tool with string arguments. */
public final class ToolInvocation {

    private final String toolName;
    private final Map<String, String> arguments;

    public ToolInvocation(String toolName, Map<String, String> arguments) {
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("toolName must not be null or empty");
        }
        this.toolName = toolName;
        this.arguments = copyArguments(arguments);
    }

    public String toolName() {
        return toolName;
    }

    /** Return the arguments in their given order. */
    public Map<String, String> arguments() {
        return arguments;
    }

    /** Return the argument value, or {@code null} if absent. */
    public String argument(String name) {
        return arguments.get(name);
    }

    private static Map<String, String> copyArguments(Map<String, String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copy = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("argument names and values must not be null");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public String toString() {
        return "ToolInvocation{" + toolName + ", arguments=" + arguments.keySet() + "}";
    }
}
