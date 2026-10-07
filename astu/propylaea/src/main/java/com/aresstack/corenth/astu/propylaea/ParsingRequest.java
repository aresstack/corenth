package com.aresstack.corenth.astu.propylaea;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * Input of a {@link SourceParser}: already obtained source text plus its provenance.
 *
 * <p>Propylaea never reads resources itself. The caller supplies the text (typically from the
 * shallow extraction stage), the resource it came from, the unit name the source is known by
 * (member or object name) and a language hint, which may be {@link SourceLanguage#UNKNOWN}.
 */
public final class ParsingRequest {

    private final VirtualResourceRef source;
    private final String unitName;
    private final SourceLanguage language;
    private final String text;

    public ParsingRequest(VirtualResourceRef source, String unitName, SourceLanguage language, String text) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (unitName == null || unitName.trim().isEmpty()) {
            throw new IllegalArgumentException("unitName must not be null or blank");
        }
        if (language == null) {
            throw new IllegalArgumentException("language must not be null; use UNKNOWN");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.source = source;
        this.unitName = unitName.trim();
        this.language = language;
        this.text = text;
    }

    public VirtualResourceRef source() {
        return source;
    }

    /** Returns the name the source unit is known by, for example a library member name. */
    public String unitName() {
        return unitName;
    }

    /** Returns the language hint; {@link SourceLanguage#UNKNOWN} asks for detection. */
    public SourceLanguage language() {
        return language;
    }

    public String text() {
        return text;
    }

    /** Returns a copy of this request with another language. */
    public ParsingRequest withLanguage(SourceLanguage otherLanguage) {
        return new ParsingRequest(source, unitName, otherLanguage, text);
    }
}
