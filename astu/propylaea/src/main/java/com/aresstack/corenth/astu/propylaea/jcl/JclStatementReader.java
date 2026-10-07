package com.aresstack.corenth.astu.propylaea.jcl;

import com.aresstack.corenth.astu.propylaea.SourceDiagnostic;
import com.aresstack.corenth.astu.propylaea.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns JCL card images into logical {@link JclStatement}s.
 *
 * <p>Handled rules of the z/OS JCL reference format:
 * <ul>
 *   <li>Columns 72-80 (continuation indicator and sequence number) are ignored.</li>
 *   <li>{@code //*} lines are comments; {@code /*} lines are delimiters or JES control statements.</li>
 *   <li>The name field starts in column 3; a blank column 3 means an unnamed statement.</li>
 *   <li>The operand field ends at the first blank outside apostrophes; the rest is a comment.</li>
 *   <li>An operand field ending with a comma continues on the next {@code //} line; an apostrophe
 *       string still open at column 71 continues in column 16 of the next line.</li>
 *   <li>{@code DD *} data ends at the next {@code //} or {@code /*} line; {@code DD DATA} data ends
 *       only at {@code /*} or at the {@code DLM} delimiter.</li>
 * </ul>
 */
final class JclStatementReader {

    private static final int STATEMENT_COLUMNS = 71;
    private static final int QUOTED_CONTINUATION_COLUMN = 16;

    private final String[] lines;
    private final List<SourceDiagnostic> diagnostics;
    private final List<JclStatement> statements = new ArrayList<JclStatement>();

    private boolean inStreamData;
    private String dataDelimiter;
    private int index;

    JclStatementReader(String[] lines, List<SourceDiagnostic> diagnostics) {
        this.lines = lines;
        this.diagnostics = diagnostics;
    }

    List<JclStatement> read() {
        for (index = 0; index < lines.length; index++) {
            String line = card(index);
            if (inStreamData && !leavesInStreamData(line)) {
                continue;
            }
            if (line.startsWith("//*") || line.startsWith("/*")) {
                continue;
            }
            if (!line.startsWith("//")) {
                if (!line.trim().isEmpty()) {
                    diagnose(index + 1, "line is neither a JCL statement nor in-stream data");
                }
                continue;
            }
            readStatement(line);
        }
        return statements;
    }

    /** Returns {@code true} if this line ends in-stream data and must itself be processed as JCL. */
    private boolean leavesInStreamData(String line) {
        if (dataDelimiter != null) {
            if (line.startsWith(dataDelimiter)) {
                inStreamData = false;
            }
            return false;
        }
        if (line.startsWith("/*")) {
            inStreamData = false;
            return false;
        }
        if (line.startsWith("//")) {
            inStreamData = false;
            return true;
        }
        return false;
    }

    private void readStatement(String line) {
        int startLine = index + 1;
        String body = line.substring(2);
        if (body.trim().isEmpty()) {
            return;
        }
        String name = null;
        int position = 0;
        if (body.charAt(0) != ' ') {
            position = nextBlank(body, 0);
            name = body.substring(0, position).toUpperCase(Locale.ROOT);
        }
        position = skipBlanks(body, position);
        if (position >= body.length()) {
            diagnose(startLine, "statement " + (name == null ? "" : name + " ") + "has no operation");
            return;
        }
        int operationEnd = nextBlank(body, position);
        String operation = body.substring(position, operationEnd).toUpperCase(Locale.ROOT);
        String operands = body.substring(skipBlanks(body, operationEnd));

        OperandScan scan = OperandScan.of(operands);
        while (scan.continues()) {
            int next = nextContinuationLine();
            if (next < 0) {
                diagnose(startLine, operation + " operand field ends with a continuation but no continuation line follows");
                break;
            }
            index = next;
            String continuation = card(next);
            String piece;
            if (scan.openApostrophe) {
                piece = continuation.length() >= QUOTED_CONTINUATION_COLUMN
                        ? continuation.substring(QUOTED_CONTINUATION_COLUMN - 1) : "";
            } else {
                piece = continuation.substring(skipBlanks(continuation, 2));
            }
            scan = OperandScan.of(scan.field + piece);
        }
        JclStatement statement = new JclStatement(name, operation, scan.field, startLine, index + 1);
        statements.add(statement);
        if (operation.equals("DD")) {
            enterInStreamDataIfRequested(statement);
        }
    }

    private void enterInStreamDataIfRequested(JclStatement statement) {
        boolean star = statement.positional().contains("*");
        boolean data = statement.positional().contains("DATA") || statement.positional().contains("data");
        if (!star && !data) {
            return;
        }
        inStreamData = true;
        String delimiter = statement.keyword("DLM");
        if (delimiter != null) {
            dataDelimiter = stripApostrophes(delimiter);
        } else {
            dataDelimiter = data ? "/*" : null;
        }
    }

    /** Returns the index of the next continuation card, skipping comments, or -1. */
    private int nextContinuationLine() {
        for (int next = index + 1; next < lines.length; next++) {
            String candidate = card(next);
            if (candidate.startsWith("//*")) {
                continue;
            }
            if (candidate.startsWith("//") && candidate.length() > 2 && candidate.charAt(2) == ' '
                    && !candidate.substring(2).trim().isEmpty()) {
                return next;
            }
            return -1;
        }
        return -1;
    }

    private String card(int lineIndex) {
        String line = lines[lineIndex];
        return line.length() > STATEMENT_COLUMNS ? line.substring(0, STATEMENT_COLUMNS) : line;
    }

    private void diagnose(int line, String message) {
        diagnostics.add(new SourceDiagnostic(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, SourceLocation.line(line), message));
    }

    static String stripApostrophes(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '\'' && trimmed.charAt(trimmed.length() - 1) == '\'') {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int nextBlank(String text, int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) != ' ') {
            i++;
        }
        return i;
    }

    private static int skipBlanks(String text, int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** The operand field of a card, cut at the first blank outside apostrophes. */
    private static final class OperandScan {

        final String field;
        final boolean openApostrophe;

        private OperandScan(String field, boolean openApostrophe) {
            this.field = field;
            this.openApostrophe = openApostrophe;
        }

        static OperandScan of(String text) {
            boolean quoted = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\'') {
                    quoted = !quoted;
                } else if (c == ' ' && !quoted) {
                    return new OperandScan(text.substring(0, i), false);
                }
            }
            return new OperandScan(text, quoted);
        }

        boolean continues() {
            return openApostrophe || field.endsWith(",");
        }
    }
}
