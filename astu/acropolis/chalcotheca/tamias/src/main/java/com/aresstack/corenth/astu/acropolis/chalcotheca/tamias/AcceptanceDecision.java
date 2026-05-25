package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

/**
 * The outcome of a policy evaluation: whether a resource should be processed.
 */
public enum AcceptanceDecision {
    /** The resource is accepted for processing/indexing. */
    ACCEPT,
    /** The resource is denied/skipped. */
    DENY
}
