package com.aresstack.corenth.astu.acropolis;

/**
 * The steps of the resource lifecycle, in the order of the standard plan (#10 Slice 4).
 *
 * <p>A step type names behaviour, not an implementation: the coordinator executes it through
 * its ports (Tamias policies, the mediated counter, the content inspector, the lexical index and
 * the archive facts).
 */
public enum ResourceProcessingStepType {
    /** Tamias indexing rules on scheme and patterns before any acquisition (size unknown). */
    INDEXING_POLICY_BEFORE_ACQUISITION,
    /** Mediated metadata read and Tamias size rules before the payload is acquired (#10 Slice 5). */
    SOURCE_METADATA,
    /** Mediated read through the archive counter, including access preparation. */
    MEDIATED_ACQUISITION,
    /** Tamias indexing rules with the acquired size. */
    INDEXING_POLICY_WITH_SIZE,
    /** Tamias change detection and derivative disposition against the resource record (#5, #33). */
    CHANGE_DETECTION,
    /** Detection and extraction of textual content. */
    CONTENT_INSPECTION,
    /** Writing the lexical index. */
    LEXICAL_INDEXING,
    /** Recording the observed and indexed version in the resource record. */
    RECORD_UPDATE,
    /** Removing derived state (lexical entries, indexed-version fact) after a stop or withdrawal. */
    DERIVED_STATE_CLEANUP
}
