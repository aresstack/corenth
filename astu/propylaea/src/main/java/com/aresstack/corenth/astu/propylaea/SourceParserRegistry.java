package com.aresstack.corenth.astu.propylaea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable dispatch table from {@link SourceLanguage} to the explicitly registered {@link SourceParser}.
 *
 * <p>At most one parser per language is allowed; a second registration is rejected instead of
 * silently shadowing the first. A request without a language hint is routed through the
 * {@link SourceLanguageDetector}. Missing parsers yield {@link ParsingFailureReason#UNSUPPORTED_LANGUAGE}.
 */
public final class SourceParserRegistry {

    private final SourceLanguageDetector detector;
    private final Map<SourceLanguage, SourceParser> parsers;

    public SourceParserRegistry(SourceLanguageDetector detector, List<? extends SourceParser> parsers) {
        if (detector == null) {
            throw new IllegalArgumentException("detector must not be null");
        }
        if (parsers == null) {
            throw new IllegalArgumentException("parsers must not be null");
        }
        Map<SourceLanguage, SourceParser> byLanguage = new EnumMap<SourceLanguage, SourceParser>(SourceLanguage.class);
        for (SourceParser parser : parsers) {
            if (parser == null) {
                throw new IllegalArgumentException("parsers must not contain null");
            }
            SourceLanguage language = parser.language();
            if (language == null || language == SourceLanguage.UNKNOWN) {
                throw new IllegalArgumentException("parser must declare a concrete language: " + parser);
            }
            if (byLanguage.containsKey(language)) {
                throw new IllegalArgumentException("duplicate parser for " + language);
            }
            byLanguage.put(language, parser);
        }
        this.detector = detector;
        this.parsers = Collections.unmodifiableMap(byLanguage);
    }

    /** Returns the languages with a registered parser, in enum order. */
    public List<SourceLanguage> supportedLanguages() {
        return Collections.unmodifiableList(new ArrayList<SourceLanguage>(parsers.keySet()));
    }

    /** Returns {@code true} if a parser is registered for the language. */
    public boolean supports(SourceLanguage language) {
        return parsers.containsKey(language);
    }

    /**
     * Parses the request with the parser for its language, detecting the language first if the
     * hint is {@link SourceLanguage#UNKNOWN}.
     */
    public ParsingResult parse(ParsingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        SourceLanguage language = request.language();
        if (language == SourceLanguage.UNKNOWN) {
            language = detector.detect(request.text());
        }
        SourceParser parser = language == null ? null : parsers.get(language);
        if (parser == null) {
            SourceLanguage attempted = language == null ? SourceLanguage.UNKNOWN : language;
            return ParsingResult.failure(request.source(), attempted, ParsingFailureReason.UNSUPPORTED_LANGUAGE,
                    "no parser registered for language " + attempted);
        }
        ParsingResult result = parser.parse(request.withLanguage(language));
        if (result == null) {
            throw new IllegalStateException("parser returned null for " + language);
        }
        return result;
    }
}
