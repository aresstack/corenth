package com.aresstack.corenth.astu.propylaea;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * Outcome of a parse: either a {@link ProgramStructure} or a typed failure.
 *
 * <p>Partially understood sources are successes with {@link ProgramStructure#diagnostics()};
 * failures are reserved for input that yields no structure at all.
 */
public final class ParsingResult {

    private final VirtualResourceRef source;
    private final SourceLanguage language;
    private final ProgramStructure structure;
    private final ParsingFailureReason failureReason;
    private final String failureMessage;

    private ParsingResult(VirtualResourceRef source, SourceLanguage language, ProgramStructure structure,
                          ParsingFailureReason failureReason, String failureMessage) {
        this.source = source;
        this.language = language;
        this.structure = structure;
        this.failureReason = failureReason;
        this.failureMessage = failureMessage;
    }

    /** Creates a successful result. */
    public static ParsingResult success(ProgramStructure structure) {
        if (structure == null) {
            throw new IllegalArgumentException("structure must not be null");
        }
        return new ParsingResult(structure.source(), structure.language(), structure, null, null);
    }

    /**
     * Creates a failed result.
     *
     * @param language the language that was attempted, {@link SourceLanguage#UNKNOWN} if none
     */
    public static ParsingResult failure(VirtualResourceRef source, SourceLanguage language,
                                        ParsingFailureReason reason, String message) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (language == null) {
            throw new IllegalArgumentException("language must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
        return new ParsingResult(source, language, null, reason, message);
    }

    public boolean isSuccess() {
        return structure != null;
    }

    public VirtualResourceRef source() {
        return source;
    }

    /** Returns the language that was parsed or attempted. */
    public SourceLanguage language() {
        return language;
    }

    /** Returns the structure, or {@code null} for a failure. */
    public ProgramStructure structure() {
        return structure;
    }

    /** Returns the failure reason, or {@code null} for a success. */
    public ParsingFailureReason failureReason() {
        return failureReason;
    }

    /** Returns the failure message, or {@code null} for a success. */
    public String failureMessage() {
        return failureMessage;
    }

    @Override
    public String toString() {
        return isSuccess()
                ? "ParsingResult{success, " + language + ", " + source + "}"
                : "ParsingResult{" + failureReason + ", " + language + ", " + source + ": " + failureMessage + "}";
    }
}
