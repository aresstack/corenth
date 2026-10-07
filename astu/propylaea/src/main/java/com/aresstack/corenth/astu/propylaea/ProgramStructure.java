package com.aresstack.corenth.astu.propylaea;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The language-neutral result of parsing one source unit: its components, the relations they
 * originate and the diagnostics for constructs that could not be modelled.
 *
 * <p>The first component is always the unit itself. Components, relations and diagnostics keep
 * the order in which the parser met them in the source, so equal input yields equal structures.
 * The structure is derived data: it is not a second store of the source and holds no text.
 *
 * <p>Invariants checked on construction:
 * <ul>
 *   <li>component names are unique per kind;</li>
 *   <li>every relation starts at a component of this structure;</li>
 *   <li>every {@link CodeComponentRef.Binding#LOCAL} target names a component of the expected kind.</li>
 * </ul>
 */
public final class ProgramStructure {

    private final VirtualResourceRef source;
    private final SourceLanguage language;
    private final List<CodeComponent> components;
    private final List<CodeRelation> relations;
    private final List<SourceDiagnostic> diagnostics;

    public ProgramStructure(VirtualResourceRef source, SourceLanguage language,
                            List<CodeComponent> components, List<CodeRelation> relations,
                            List<SourceDiagnostic> diagnostics) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (language == null || language == SourceLanguage.UNKNOWN) {
            throw new IllegalArgumentException("language must be a concrete language");
        }
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("components must contain at least the unit itself");
        }
        if (relations == null) {
            throw new IllegalArgumentException("relations must not be null");
        }
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics must not be null");
        }
        this.source = source;
        this.language = language;
        this.components = immutableCopy(components, "components");
        this.relations = immutableCopy(relations, "relations");
        this.diagnostics = immutableCopy(diagnostics, "diagnostics");
        verifyComponentNamesAreUnique();
        verifyRelationsAreAnchored();
    }

    /** Returns the resource the text was read from. */
    public VirtualResourceRef source() {
        return source;
    }

    public SourceLanguage language() {
        return language;
    }

    /** Returns the component that represents the whole source unit. */
    public CodeComponent unit() {
        return components.get(0);
    }

    /** Returns all components, starting with {@link #unit()}, in source order. */
    public List<CodeComponent> components() {
        return components;
    }

    /** Returns all relations in source order. */
    public List<CodeRelation> relations() {
        return relations;
    }

    /** Returns the relations of the given kind in source order. */
    public List<CodeRelation> relations(RelationKind kind) {
        List<CodeRelation> matching = new ArrayList<CodeRelation>();
        for (CodeRelation relation : relations) {
            if (relation.kind() == kind) {
                matching.add(relation);
            }
        }
        return Collections.unmodifiableList(matching);
    }

    public List<SourceDiagnostic> diagnostics() {
        return diagnostics;
    }

    /** Returns the component with the given name and kind, or {@code null}. */
    public CodeComponent component(String name, CodeComponentKind kind) {
        for (CodeComponent component : components) {
            if (component.kind() == kind && component.name().equals(name)) {
                return component;
            }
        }
        return null;
    }

    private void verifyComponentNamesAreUnique() {
        Set<String> keys = new HashSet<String>();
        for (CodeComponent component : components) {
            if (!keys.add(component.kind() + ":" + component.name())) {
                throw new IllegalArgumentException("duplicate component " + component.kind() + " " + component.name());
            }
        }
    }

    private void verifyRelationsAreAnchored() {
        Set<CodeComponent> known = new HashSet<CodeComponent>(components);
        for (CodeRelation relation : relations) {
            if (!known.contains(relation.from())) {
                throw new IllegalArgumentException("relation starts at unknown component: " + relation);
            }
            CodeComponentRef target = relation.target();
            if (target.binding() == CodeComponentRef.Binding.LOCAL
                    && component(target.name(), target.expectedKind()) == null) {
                throw new IllegalArgumentException("local target is not defined in this unit: " + relation);
            }
        }
    }

    private static <T> List<T> immutableCopy(List<T> values, String label) {
        List<T> copy = new ArrayList<T>(values);
        for (T value : copy) {
            if (value == null) {
                throw new IllegalArgumentException(label + " must not contain null");
            }
        }
        return Collections.unmodifiableList(copy);
    }
}
