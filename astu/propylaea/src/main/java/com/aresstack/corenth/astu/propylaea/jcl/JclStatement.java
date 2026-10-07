package com.aresstack.corenth.astu.propylaea.jcl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One logical JCL statement after continuation lines have been joined.
 *
 * <p>The operand field is split at top-level commas (outside apostrophes and parentheses) into
 * positional operands and {@code KEYWORD=value} operands. Values keep their original spelling,
 * including apostrophes and parentheses.
 */
final class JclStatement {

    private final String name;
    private final String operation;
    private final String operandField;
    private final List<String> positional;
    private final Map<String, String> keywords;
    private final int startLine;
    private final int endLine;

    JclStatement(String name, String operation, String operandField, int startLine, int endLine) {
        this.name = name;
        this.operation = operation;
        this.operandField = operandField;
        this.startLine = startLine;
        this.endLine = endLine;
        List<String> positionalOperands = new ArrayList<String>();
        Map<String, String> keywordOperands = new LinkedHashMap<String, String>();
        for (String operand : splitTopLevel(operandField, ',')) {
            int equals = keywordSeparator(operand);
            if (equals > 0) {
                String key = operand.substring(0, equals).trim().toUpperCase(Locale.ROOT);
                if (!keywordOperands.containsKey(key)) {
                    keywordOperands.put(key, operand.substring(equals + 1).trim());
                }
            } else {
                positionalOperands.add(operand.trim());
            }
        }
        this.positional = Collections.unmodifiableList(positionalOperands);
        this.keywords = Collections.unmodifiableMap(keywordOperands);
    }

    /** Returns the name field in upper case, or {@code null} if the name field is blank. */
    String name() {
        return name;
    }

    /** Returns the operation in upper case, for example {@code EXEC}. */
    String operation() {
        return operation;
    }

    /** Returns the joined operand field without comments. */
    String operandField() {
        return operandField;
    }

    List<String> positional() {
        return positional;
    }

    /** Returns the value of a keyword operand, or {@code null}. */
    String keyword(String key) {
        return keywords.get(key);
    }

    int startLine() {
        return startLine;
    }

    int endLine() {
        return endLine;
    }

    /** Splits text at the separator where it is outside apostrophes and parentheses; drops empty parts. */
    static List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<String>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (!quoted && depth == 0 && c == separator) {
                addIfNotBlank(parts, text.substring(start, i));
                start = i + 1;
            }
        }
        addIfNotBlank(parts, text.substring(start));
        return parts;
    }

    private static void addIfNotBlank(List<String> parts, String part) {
        if (!part.trim().isEmpty()) {
            parts.add(part.trim());
        }
    }

    private static int keywordSeparator(String operand) {
        for (int i = 0; i < operand.length(); i++) {
            char c = operand.charAt(i);
            if (c == '=') {
                return i;
            }
            if (c == '\'' || c == '(') {
                return -1;
            }
        }
        return -1;
    }
}
