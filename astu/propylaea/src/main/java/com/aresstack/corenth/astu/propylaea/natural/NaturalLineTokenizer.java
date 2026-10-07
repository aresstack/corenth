package com.aresstack.corenth.astu.propylaea.natural;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits one physical Natural source line into tokens, dropping comments.
 *
 * <p>Handled lexical rules:
 * <ul>
 *   <li>A line whose first non-blank characters are {@code **}, {@code /*}, or a single {@code *}
 *       followed by a blank (or nothing) is a comment line.</li>
 *   <li>{@code /*} outside a literal starts a comment that runs to the end of the line.</li>
 *   <li>Alphanumeric constants are enclosed in {@code '} or {@code "}; a doubled delimiter inside
 *       stands for one delimiter character. A constant not closed on its line is reported as
 *       unterminated and runs to the end of the line.</li>
 * </ul>
 */
final class NaturalLineTokenizer {

    private static final String SYMBOLS = "(),=<>:;";

    /** Tokens of one line plus the lexical problems found on it. */
    static final class Line {

        private final List<NaturalToken> tokens;
        private final boolean unterminatedLiteral;

        Line(List<NaturalToken> tokens, boolean unterminatedLiteral) {
            this.tokens = Collections.unmodifiableList(tokens);
            this.unterminatedLiteral = unterminatedLiteral;
        }

        List<NaturalToken> tokens() {
            return tokens;
        }

        boolean unterminatedLiteral() {
            return unterminatedLiteral;
        }

        boolean isEmpty() {
            return tokens.isEmpty();
        }
    }

    private NaturalLineTokenizer() {
    }

    static Line tokenize(String line) {
        List<NaturalToken> tokens = new ArrayList<NaturalToken>();
        if (isCommentLine(line)) {
            return new Line(tokens, false);
        }
        int length = line.length();
        int i = 0;
        while (i < length) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (startsComment(line, i)) {
                break;
            } else if (c == '\'' || c == '"') {
                StringBuilder literal = new StringBuilder();
                int j = i + 1;
                boolean closed = false;
                while (j < length) {
                    char d = line.charAt(j);
                    if (d == c) {
                        if (j + 1 < length && line.charAt(j + 1) == c) {
                            literal.append(c);
                            j += 2;
                            continue;
                        }
                        closed = true;
                        j++;
                        break;
                    }
                    literal.append(d);
                    j++;
                }
                tokens.add(new NaturalToken(NaturalToken.Type.LITERAL, literal.toString()));
                if (!closed) {
                    return new Line(tokens, true);
                }
                i = j;
            } else if (SYMBOLS.indexOf(c) >= 0) {
                tokens.add(new NaturalToken(NaturalToken.Type.SYMBOL, String.valueOf(c)));
                i++;
            } else {
                int j = i;
                while (j < length) {
                    char d = line.charAt(j);
                    if (Character.isWhitespace(d) || d == '\'' || d == '"' || SYMBOLS.indexOf(d) >= 0
                            || startsComment(line, j)) {
                        break;
                    }
                    j++;
                }
                tokens.add(new NaturalToken(NaturalToken.Type.WORD, line.substring(i, j)));
                i = j;
            }
        }
        return new Line(tokens, false);
    }

    private static boolean startsComment(String line, int index) {
        return index + 1 < line.length() && line.charAt(index) == '/' && line.charAt(index + 1) == '*';
    }

    private static boolean isCommentLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("**") || trimmed.startsWith("/*")) {
            return true;
        }
        return trimmed.equals("*") || (trimmed.startsWith("*") && Character.isWhitespace(trimmed.charAt(1)));
    }
}
