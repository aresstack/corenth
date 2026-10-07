package com.aresstack.corenth.astu.propylaea;

/**
 * A machine-readable remark about a construct the parser met but could not model completely.
 *
 * <p>Diagnostics never abort parsing. They make the limits of a line-oriented extractor visible
 * instead of silently dropping or guessing relations.
 */
public final class SourceDiagnostic {

    /** Reason code of a diagnostic. */
    public enum Code {
        /** A string literal is not closed on its line. */
        UNTERMINATED_LITERAL,
        /** A block (data definition, subroutine, procedure) is not closed before the end of input. */
        UNTERMINATED_BLOCK,
        /** A closing statement has no matching opening statement. */
        UNMATCHED_BLOCK_END,
        /** A statement is recognised but its operand is missing or not in a supported form. */
        UNSUPPORTED_CONSTRUCT,
        /** A reference inside the unit could not be bound; the relation was emitted with the best known name or omitted. */
        UNRESOLVED_REFERENCE
    }

    private final Code code;
    private final SourceLocation location;
    private final String message;

    public SourceDiagnostic(Code code, SourceLocation location, String message) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
        this.code = code;
        this.location = location;
        this.message = message;
    }

    public Code code() {
        return code;
    }

    public SourceLocation location() {
        return location;
    }

    /** Returns an English, human-readable explanation; not meant for programmatic matching. */
    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceDiagnostic)) return false;
        SourceDiagnostic that = (SourceDiagnostic) o;
        return code == that.code && location.equals(that.location) && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * code.hashCode() + location.hashCode()) + message.hashCode();
    }

    @Override
    public String toString() {
        return code + " @" + location + ": " + message;
    }
}
