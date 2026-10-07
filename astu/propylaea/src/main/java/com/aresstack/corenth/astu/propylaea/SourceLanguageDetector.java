package com.aresstack.corenth.astu.propylaea;

/**
 * Port that guesses the language of source text when the caller has no reliable hint.
 */
public interface SourceLanguageDetector {

    /**
     * Returns the detected language, or {@link SourceLanguage#UNKNOWN} when no language is evident.
     *
     * @param text the source text; never {@code null}
     */
    SourceLanguage detect(String text);
}
