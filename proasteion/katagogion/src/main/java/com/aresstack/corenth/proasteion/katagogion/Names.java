package com.aresstack.corenth.proasteion.katagogion;

import java.util.regex.Pattern;

/** Validates the identifiers used for plugins, tools and tool parameters. */
final class Names {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*(?:[.-][a-z0-9_]+)*");
    private static final int MAX_LENGTH = 64;

    private Names() {
    }

    /** Return the identifier unchanged or reject it when it is not a lower-case dotted identifier. */
    static String requireIdentifier(String value, String role) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(role + " must not be null or empty");
        }
        if (value.length() > MAX_LENGTH || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(role + " must be a lower-case identifier of at most "
                    + MAX_LENGTH + " characters (letters, digits, '_', '.', '-'): " + value);
        }
        return value;
    }

    /** Return the text or the empty string for {@code null}. */
    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
