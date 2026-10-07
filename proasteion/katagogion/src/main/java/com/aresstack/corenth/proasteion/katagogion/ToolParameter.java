package com.aresstack.corenth.proasteion.katagogion;

/**
 * Immutable declaration of one string argument a tool accepts.
 *
 * <p>Arguments are plain strings so that a later transport adapter (for example an MCP server
 * after the #44 decision) can expose the declaration without a JSON-schema library.
 */
public final class ToolParameter {

    private final String name;
    private final String description;
    private final boolean required;

    private ToolParameter(String name, String description, boolean required) {
        this.name = Names.requireIdentifier(name, "parameter name");
        this.description = Names.nullToEmpty(description);
        this.required = required;
    }

    /** Declare a mandatory argument. */
    public static ToolParameter required(String name, String description) {
        return new ToolParameter(name, description, true);
    }

    /** Declare an optional argument. */
    public static ToolParameter optional(String name, String description) {
        return new ToolParameter(name, description, false);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ToolParameter)) return false;
        ToolParameter other = (ToolParameter) o;
        return required == other.required && name.equals(other.name) && description.equals(other.description);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * name.hashCode() + description.hashCode()) + (required ? 1 : 0);
    }

    @Override
    public String toString() {
        return "ToolParameter{" + name + (required ? ", required" : ", optional") + "}";
    }
}
