package com.aresstack.corenth.astu.propylaea;

/**
 * A typed edge from a component of a source unit to a target, with the statement that caused it.
 *
 * <p>{@link #statement()} keeps the language-specific keyword (for example {@code CALLNAT},
 * {@code FETCH RETURN}, {@code EXEC PGM}) so that consumers can refine the neutral
 * {@link RelationKind} without re-parsing. Every occurrence is a separate relation; consumers
 * that need distinct targets deduplicate themselves.
 */
public final class CodeRelation {

    private final RelationKind kind;
    private final CodeComponent from;
    private final CodeComponentRef target;
    private final String statement;
    private final SourceLocation location;

    public CodeRelation(RelationKind kind, CodeComponent from, CodeComponentRef target,
                        String statement, SourceLocation location) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (from == null) {
            throw new IllegalArgumentException("from must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (statement == null || statement.trim().isEmpty()) {
            throw new IllegalArgumentException("statement must not be null or blank");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        this.kind = kind;
        this.from = from;
        this.target = target;
        this.statement = statement;
        this.location = location;
    }

    public RelationKind kind() {
        return kind;
    }

    /** Returns the enclosing component of the same structure in which the statement occurs. */
    public CodeComponent from() {
        return from;
    }

    public CodeComponentRef target() {
        return target;
    }

    /** Returns the language-specific statement keyword that produced this relation. */
    public String statement() {
        return statement;
    }

    public SourceLocation location() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeRelation)) return false;
        CodeRelation that = (CodeRelation) o;
        return kind == that.kind && from.equals(that.from)
                && target.equals(that.target) && statement.equals(that.statement)
                && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + from.hashCode();
        result = 31 * result + target.hashCode();
        result = 31 * result + statement.hashCode();
        return 31 * result + location.hashCode();
    }

    @Override
    public String toString() {
        return from.kind() + " " + from.name() + " " + kind + " " + target + " via " + statement + " @" + location;
    }
}
