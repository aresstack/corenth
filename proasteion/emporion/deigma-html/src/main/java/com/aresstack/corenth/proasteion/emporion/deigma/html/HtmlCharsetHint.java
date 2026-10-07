package com.aresstack.corenth.proasteion.emporion.deigma.html;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.List;
import java.util.Locale;

/**
 * Reads the {@code charset} parameter of a transport content-type hint.
 */
final class HtmlCharsetHint {

    private HtmlCharsetHint() {
    }

    /**
     * Returns the canonical name of a supported charset named by the hint, or {@code null}
     * if the hint names none, so that the document itself is sniffed. An unsupported
     * charset adds a warning and also yields {@code null}.
     */
    static String fromContentType(String contentTypeHint, List<String> warnings) {
        if (contentTypeHint == null) {
            return null;
        }
        for (String parameter : contentTypeHint.split(";")) {
            String trimmed = parameter.trim();
            int equals = trimmed.indexOf('=');
            if (equals < 0 || !"charset".equals(trimmed.substring(0, equals).trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            String name = unquote(trimmed.substring(equals + 1).trim());
            if (name.isEmpty()) {
                return null;
            }
            try {
                if (Charset.isSupported(name)) {
                    return Charset.forName(name).name();
                }
            } catch (IllegalCharsetNameException e) {
                // reported below like any other unsupported charset
            }
            warnings.add("UNSUPPORTED_CHARSET_HINT: " + name + "; the document encoding is sniffed instead");
            return null;
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                && value.charAt(value.length() - 1) == value.charAt(0)) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }
}
