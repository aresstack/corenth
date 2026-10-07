package com.aresstack.corenth.astu.propylaea;

import java.util.Locale;

/**
 * Default {@link SourceLanguageDetector} based on unambiguous structural keywords.
 *
 * <p>Adapted from the MainframeMate code-analytics heuristic, with two corrections: comment lines
 * are ignored, and a weak single hit is not enough to claim a language that has stronger markers.
 * Rules, in order:
 * <ol>
 *   <li>The first significant line starts with {@code //}: JCL.</li>
 *   <li>A non-comment line contains a COBOL division header or {@code PROGRAM-ID}: COBOL.</li>
 *   <li>A line starts with a Natural-only statement ({@code DEFINE DATA}, {@code END-DEFINE},
 *       {@code CALLNAT}, {@code DEFINE SUBROUTINE}, {@code END-SUBROUTINE}): NATURAL.</li>
 * </ol>
 * Anything else, including copybooks without such markers, is {@link SourceLanguage#UNKNOWN};
 * callers with better knowledge pass an explicit language hint instead.
 */
public final class KeywordSourceLanguageDetector implements SourceLanguageDetector {

    private static final int MAX_SCANNED_LINES = 200;

    private static final String[] COBOL_MARKERS = {
            "IDENTIFICATION DIVISION", "ID DIVISION", "PROCEDURE DIVISION", "PROGRAM-ID"
    };

    private static final String[] NATURAL_STATEMENTS = {
            "DEFINE DATA", "END-DEFINE", "CALLNAT ", "DEFINE SUBROUTINE ", "END-SUBROUTINE"
    };

    @Override
    public SourceLanguage detect(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        String[] lines = text.split("\r\n|\r|\n", MAX_SCANNED_LINES + 1);
        int scanned = Math.min(lines.length, MAX_SCANNED_LINES);
        String firstSignificant = null;
        boolean cobol = false;
        boolean natural = false;
        for (int i = 0; i < scanned; i++) {
            String line = lines[i];
            String trimmed = line.trim().toUpperCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (firstSignificant == null) {
                firstSignificant = trimmed;
            }
            if (!isCobolCommentLine(line) && containsAny(trimmed, COBOL_MARKERS)) {
                cobol = true;
            }
            if (startsWithAny(trimmed + " ", NATURAL_STATEMENTS)) {
                natural = true;
            }
        }
        if (firstSignificant == null) {
            return SourceLanguage.UNKNOWN;
        }
        if (firstSignificant.startsWith("//")) {
            return SourceLanguage.JCL;
        }
        if (cobol) {
            return SourceLanguage.COBOL;
        }
        if (natural) {
            return SourceLanguage.NATURAL;
        }
        return SourceLanguage.UNKNOWN;
    }

    private static boolean isCobolCommentLine(String line) {
        if (line.length() > 6) {
            char indicator = line.charAt(6);
            if (indicator == '*' || indicator == '/') {
                return true;
            }
        }
        return line.trim().startsWith("*>");
    }

    private static boolean containsAny(String text, String[] markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithAny(String text, String[] prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
