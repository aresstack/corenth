package com.aresstack.corenth.astu.propylaea.cobol;

import com.aresstack.corenth.astu.propylaea.SourceDiagnostic;
import com.aresstack.corenth.astu.propylaea.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tokenizes COBOL source in fixed reference format into one token stream.
 *
 * <p>Handled rules:
 * <ul>
 *   <li>Columns 1-6 (sequence area) and 73-80 (identification area) are ignored.</li>
 *   <li>Indicator column 7: {@code *} and {@code /} mark comment lines, {@code D} marks debugging
 *       lines (treated as comments because debugging mode is off by default), {@code -} continues
 *       an alphanumeric literal after the first delimiter in area B.</li>
 *   <li>{@code *>} outside a literal starts a floating comment.</li>
 *   <li>Commas and semicolons are separators like blanks; a period followed by a blank or the
 *       end of the line is a separator period.</li>
 * </ul>
 * A {@code >>SOURCE FORMAT FREE} directive stops tokenization with a diagnostic because free
 * format is not supported.
 */
final class CobolTokenizer {

    private static final int INDICATOR_INDEX = 6;
    private static final int AREA_A_INDEX = 7;
    private static final int LAST_COLUMN = 72;

    private final String[] lines;
    private final List<SourceDiagnostic> diagnostics;
    private final List<CobolToken> tokens = new ArrayList<CobolToken>();

    private StringBuilder openLiteral;
    private char openDelimiter;
    private int openLine;
    private int openColumn;

    CobolTokenizer(String[] lines, List<SourceDiagnostic> diagnostics) {
        this.lines = lines;
        this.diagnostics = diagnostics;
    }

    List<CobolToken> tokenize() {
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String card = lines[i].length() > LAST_COLUMN ? lines[i].substring(0, LAST_COLUMN) : lines[i];
            if (card.toUpperCase(Locale.ROOT).replace(" ", "").contains(">>SOURCEFORMATFREE")
                    || card.toUpperCase(Locale.ROOT).replace(" ", "").contains(">>SOURCEFREE")) {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, lineNumber,
                        "free-format source is not supported; the rest of the unit is not analysed");
                closeOpenLiteral();
                return tokens;
            }
            char indicator = card.length() > INDICATOR_INDEX ? card.charAt(INDICATOR_INDEX) : ' ';
            if (indicator == '*' || indicator == '/' || indicator == 'D' || indicator == 'd') {
                continue;
            }
            int start = AREA_A_INDEX;
            if (indicator == '-' && openLiteral != null) {
                int resume = firstDelimiter(card, start, openDelimiter);
                if (resume < 0) {
                    closeOpenLiteral();
                } else {
                    start = scanLiteral(card, resume + 1, lineNumber);
                }
            } else if (openLiteral != null) {
                closeOpenLiteral();
            }
            if (openLiteral == null) {
                scan(card, start, lineNumber);
            }
        }
        closeOpenLiteral();
        return tokens;
    }

    private void scan(String card, int from, int lineNumber) {
        int i = from;
        while (i < card.length()) {
            char c = card.charAt(i);
            if (c == ' ' || c == ',' || c == ';' || c == '\t') {
                i++;
            } else if (c == '*' && i + 1 < card.length() && card.charAt(i + 1) == '>') {
                return;
            } else if (c == '\'' || c == '"') {
                openLiteral = new StringBuilder();
                openDelimiter = c;
                openLine = lineNumber;
                openColumn = i + 1;
                i = scanLiteral(card, i + 1, lineNumber);
                if (openLiteral != null) {
                    return;
                }
            } else if (c == '(' || c == ')') {
                tokens.add(new CobolToken(CobolToken.Type.SYMBOL, String.valueOf(c), lineNumber, i + 1));
                i++;
            } else if (c == '.' && (i + 1 >= card.length() || card.charAt(i + 1) == ' ')) {
                tokens.add(new CobolToken(CobolToken.Type.PERIOD, ".", lineNumber, i + 1));
                i++;
            } else {
                int j = i;
                while (j < card.length()) {
                    char d = card.charAt(j);
                    if (d == ' ' || d == ',' || d == ';' || d == '\t' || d == '\'' || d == '"' || d == '(' || d == ')') {
                        break;
                    }
                    if (d == '.' && (j + 1 >= card.length() || card.charAt(j + 1) == ' ')) {
                        break;
                    }
                    j++;
                }
                tokens.add(new CobolToken(CobolToken.Type.WORD, card.substring(i, j), lineNumber, i + 1));
                i = j;
            }
        }
    }

    /** Continues the open literal from the index; returns the index after it, or the card length if still open. */
    private int scanLiteral(String card, int from, int lineNumber) {
        int j = from;
        while (j < card.length()) {
            char d = card.charAt(j);
            if (d == openDelimiter) {
                if (j + 1 < card.length() && card.charAt(j + 1) == openDelimiter) {
                    openLiteral.append(d);
                    j += 2;
                    continue;
                }
                tokens.add(new CobolToken(CobolToken.Type.LITERAL, openLiteral.toString(), openLine, openColumn));
                openLiteral = null;
                return j + 1;
            }
            openLiteral.append(d);
            j++;
        }
        if (j < LAST_COLUMN) {
            // Pad to column 72 because a continued literal includes the blanks up to the margin.
            for (int pad = j; pad < LAST_COLUMN; pad++) {
                openLiteral.append(' ');
            }
        }
        return card.length();
    }

    private void closeOpenLiteral() {
        if (openLiteral != null) {
            diagnose(SourceDiagnostic.Code.UNTERMINATED_LITERAL, openLine, "alphanumeric literal is not closed");
            tokens.add(new CobolToken(CobolToken.Type.LITERAL, openLiteral.toString().trim(), openLine, openColumn));
            openLiteral = null;
        }
    }

    private static int firstDelimiter(String card, int from, char delimiter) {
        for (int i = from; i < card.length(); i++) {
            if (card.charAt(i) == delimiter) {
                return i;
            }
        }
        return -1;
    }

    private void diagnose(SourceDiagnostic.Code code, int line, String message) {
        diagnostics.add(new SourceDiagnostic(code, SourceLocation.line(line), message));
    }
}
