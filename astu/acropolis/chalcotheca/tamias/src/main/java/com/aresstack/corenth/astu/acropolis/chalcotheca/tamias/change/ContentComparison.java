package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

/**
 * The result of comparing the currently observed content with the latest observed version of
 * the record.
 *
 * <p>The orchestration (#10) computes it with the canonical Chalcotheca {@code ResourceDigest}
 * equality (same fingerprint and same size) and hands only the result to Tamias, so Tamias needs
 * neither a digest type of its own nor a dependency on Chalcotheca.
 */
public enum ContentComparison {
    /** The observed digest equals the digest of the latest observed version. */
    SAME_AS_LATEST_OBSERVED,
    /** The observed digest differs from the digest of the latest observed version. */
    DIFFERS_FROM_LATEST_OBSERVED,
    /** No record exists, so there is nothing to compare with (first sighting). */
    NOT_COMPARED
}
