package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

/**
 * The verdict of a scope, traversal or size evaluation.
 */
public enum ScopeVerdict {
    /** The resource may be processed as far as this policy is concerned. */
    ADMIT,
    /** The resource must not be processed. */
    REJECT,
    /**
     * The policy cannot decide yet because a required fact is missing (for example the size
     * before acquisition). The caller evaluates again once the fact is known; this is never an
     * admission.
     */
    UNDETERMINED
}
