package com.aresstack.corenth.astu.propylaea;

/**
 * A structural element defined inside a parsed source unit.
 *
 * <p>Names are unique per kind within one {@link ProgramStructure}; the parser normalises them to
 * upper case because the supported languages treat names case-insensitively.
 */
public final class CodeComponent {

    private final String name;
    private final CodeComponentKind kind;
    private final SourceLocation location;

    public CodeComponent(String name, CodeComponentKind kind, SourceLocation location) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        this.name = name;
        this.kind = kind;
        this.location = location;
    }

    public String name() {
        return name;
    }

    public CodeComponentKind kind() {
        return kind;
    }

    public SourceLocation location() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeComponent)) return false;
        CodeComponent that = (CodeComponent) o;
        return name.equals(that.name) && kind == that.kind && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * name.hashCode() + kind.hashCode()) + location.hashCode();
    }

    @Override
    public String toString() {
        return kind + " " + name + " @" + location;
    }
}
