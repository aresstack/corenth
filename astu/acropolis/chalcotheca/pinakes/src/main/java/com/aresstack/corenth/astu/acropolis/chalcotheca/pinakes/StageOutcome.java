package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Report, as a machine-readable code, what happened to an optional retrieval stage.
 *
 * <p>Optional stages never break lexical retrieval. When one does not apply, the result
 * says why instead of failing the whole search.
 */
public enum StageOutcome {
    /** The stage ran and shaped the result. */
    APPLIED,
    /** The plan switched the stage off. */
    DISABLED,
    /** The plan asked for the stage, but no adapter is wired. */
    NOT_CONFIGURED,
    /** The embedding client and the semantic index belong to different models. */
    MODEL_MISMATCH,
    /** There was nothing for the stage to work on. */
    NO_CANDIDATES,
    /** The adapter failed or broke its contract; the previous ranking was kept. */
    FAILED
}
