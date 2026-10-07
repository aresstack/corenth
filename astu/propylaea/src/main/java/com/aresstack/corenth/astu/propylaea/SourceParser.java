package com.aresstack.corenth.astu.propylaea;

/**
 * Port for a language-specific parser that turns source text into a {@link ProgramStructure}.
 *
 * <p>Implementations are optional plug-ins registered explicitly in a {@link SourceParserRegistry}.
 * They must be stateless or thread-confined, must not read resources themselves and must report
 * malformed or unsupported input through {@link ParsingResult} and diagnostics instead of throwing.
 */
public interface SourceParser {

    /** Returns the one language this parser handles; never {@link SourceLanguage#UNKNOWN}. */
    SourceLanguage language();

    /**
     * Parses the request text.
     *
     * @param request a request whose language equals {@link #language()}
     * @return a success with the structure, or a typed failure; never {@code null}
     */
    ParsingResult parse(ParsingRequest request);
}
