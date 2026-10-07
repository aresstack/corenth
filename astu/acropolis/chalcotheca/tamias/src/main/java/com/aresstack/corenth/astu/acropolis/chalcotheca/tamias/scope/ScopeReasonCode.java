package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

/**
 * Stable, machine-readable reason codes for scope, traversal and size decisions.
 *
 * <p>Each code implies exactly one {@link ScopeVerdict}, so a decision can never carry a
 * contradicting verdict and reason.
 */
public enum ScopeReasonCode {
    /** The resource lies at or below the scope root. */
    IN_SCOPE(ScopeVerdict.ADMIT),
    /** The resource lies outside the scope root (other scheme, authority or path). */
    OUT_OF_SCOPE(ScopeVerdict.REJECT),
    /** The resource lies within the maximum traversal depth below the scope root. */
    WITHIN_DEPTH(ScopeVerdict.ADMIT),
    /** The resource lies deeper below the scope root than the maximum traversal depth. */
    DEPTH_EXCEEDED(ScopeVerdict.REJECT),
    /** Traversal may descend into the children of the container. */
    DESCENT_ALLOWED(ScopeVerdict.ADMIT),
    /** The container's children would exceed the maximum traversal depth. */
    DEPTH_EXHAUSTED(ScopeVerdict.REJECT),
    /** No size limit is configured. */
    NO_SIZE_LIMIT(ScopeVerdict.ADMIT),
    /** The size is at or below the configured limit. */
    SIZE_WITHIN_LIMIT(ScopeVerdict.ADMIT),
    /** The size exceeds the configured limit. */
    SIZE_OVER_LIMIT(ScopeVerdict.REJECT),
    /** The size is not known yet, so a configured limit cannot be checked. */
    SIZE_UNKNOWN(ScopeVerdict.UNDETERMINED);

    private final ScopeVerdict verdict;

    ScopeReasonCode(ScopeVerdict verdict) {
        this.verdict = verdict;
    }

    /** Return the verdict implied by this reason. */
    public ScopeVerdict verdict() {
        return verdict;
    }
}
