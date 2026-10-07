package com.aresstack.corenth.astu.propylaea;

/**
 * The target of a {@link CodeRelation}, as far as a single source unit can tell.
 *
 * <p>A parser never resolves targets against other resources. It only distinguishes three cases:
 * <ul>
 *   <li>{@link Binding#LOCAL}: the target is a component defined in the same source unit.</li>
 *   <li>{@link Binding#EXTERNAL}: the target is named statically and lives outside the unit.</li>
 *   <li>{@link Binding#DYNAMIC}: the target is computed at run time (variable, symbolic
 *       parameter, referback); {@link #name()} then holds the expression as written.</li>
 * </ul>
 * Mapping an external name to a concrete resource is a later cross-resource step, not parsing.
 */
public final class CodeComponentRef {

    /** How the target of a relation is bound. */
    public enum Binding {
        LOCAL,
        EXTERNAL,
        DYNAMIC
    }

    private final String name;
    private final CodeComponentKind expectedKind;
    private final Binding binding;

    private CodeComponentRef(String name, CodeComponentKind expectedKind, Binding binding) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (expectedKind == null) {
            throw new IllegalArgumentException("expectedKind must not be null");
        }
        this.name = name;
        this.expectedKind = expectedKind;
        this.binding = binding;
    }

    /** Refers to a component defined in the same source unit. */
    public static CodeComponentRef local(String name, CodeComponentKind expectedKind) {
        return new CodeComponentRef(name, expectedKind, Binding.LOCAL);
    }

    /** Refers to a statically named component outside the source unit. */
    public static CodeComponentRef external(String name, CodeComponentKind expectedKind) {
        return new CodeComponentRef(name, expectedKind, Binding.EXTERNAL);
    }

    /** Refers to a target that is only known at run time; {@code expression} is kept verbatim. */
    public static CodeComponentRef dynamic(String expression, CodeComponentKind expectedKind) {
        return new CodeComponentRef(expression, expectedKind, Binding.DYNAMIC);
    }

    /** Returns the target name, or the verbatim expression for a dynamic target. */
    public String name() {
        return name;
    }

    /** Returns the kind the referencing statement expects the target to have. */
    public CodeComponentKind expectedKind() {
        return expectedKind;
    }

    public Binding binding() {
        return binding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeComponentRef)) return false;
        CodeComponentRef that = (CodeComponentRef) o;
        return name.equals(that.name) && expectedKind == that.expectedKind && binding == that.binding;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * name.hashCode() + expectedKind.hashCode()) + binding.hashCode();
    }

    @Override
    public String toString() {
        return binding + " " + expectedKind + " " + name;
    }
}
