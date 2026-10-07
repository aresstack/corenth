package com.aresstack.corenth.astu.propylaea;

/**
 * An inclusive, 1-based line range inside a source unit.
 *
 * <p>Lines are counted the way the parser received the text: every {@code \n}, {@code \r\n}
 * or lone {@code \r} ends one line. Columns are not modelled because the reference parsers only
 * reported line numbers and fixed-format languages assign meaning to columns differently.
 */
public final class SourceLocation {

    private final int startLine;
    private final int endLine;

    public SourceLocation(int startLine, int endLine) {
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1");
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException("endLine must be >= startLine");
        }
        this.startLine = startLine;
        this.endLine = endLine;
    }

    /** Creates a location covering exactly one line. */
    public static SourceLocation line(int line) {
        return new SourceLocation(line, line);
    }

    public int startLine() {
        return startLine;
    }

    public int endLine() {
        return endLine;
    }

    /** Returns {@code true} if the given line lies inside this range. */
    public boolean contains(int line) {
        return line >= startLine && line <= endLine;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceLocation)) return false;
        SourceLocation that = (SourceLocation) o;
        return startLine == that.startLine && endLine == that.endLine;
    }

    @Override
    public int hashCode() {
        return 31 * startLine + endLine;
    }

    @Override
    public String toString() {
        return startLine == endLine ? "L" + startLine : "L" + startLine + "-L" + endLine;
    }
}
