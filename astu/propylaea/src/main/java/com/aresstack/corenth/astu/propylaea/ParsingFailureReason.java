package com.aresstack.corenth.astu.propylaea;

/**
 * Machine-readable reason why no {@link ProgramStructure} could be produced.
 */
public enum ParsingFailureReason {

    /** No parser is registered for the language, or the language is unknown and not detectable. */
    UNSUPPORTED_LANGUAGE,

    /** The text contains no content at all (empty or whitespace only). */
    EMPTY_SOURCE,

    /** The text is not source code of the language at all, for example binary data. */
    MALFORMED_SOURCE
}
