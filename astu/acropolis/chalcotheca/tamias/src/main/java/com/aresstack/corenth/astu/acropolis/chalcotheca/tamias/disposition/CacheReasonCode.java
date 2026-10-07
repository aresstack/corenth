package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition;

/**
 * Stable, machine-readable reasons for the cache part of a {@link DerivativeDisposition}. Each
 * code implies exactly one {@link CacheAction}.
 */
public enum CacheReasonCode {
    /** No recorded version exists to validate cached derivatives against. */
    NO_RECORDED_VERSION(CacheAction.REFRESH),
    /** The content equals the latest observed version. */
    CONTENT_UNCHANGED(CacheAction.RETAIN),
    /** The content differs from the latest observed version. */
    CONTENT_CHANGED(CacheAction.REFRESH),
    /** The resource is gone at its source. */
    REMOVED_AT_SOURCE(CacheAction.INVALIDATE),
    /** The resource is neither recorded nor present at its source. */
    NOT_FOUND_AT_SOURCE(CacheAction.INVALIDATE),
    /**
     * The policies do not admit the resource for indexing; admission concerns derived index
     * state, so a cached payload stays (#10 Slice 5).
     */
    NOT_ADMITTED(CacheAction.RETAIN);

    private final CacheAction action;

    CacheReasonCode(CacheAction action) {
        this.action = action;
    }

    /** Return the cache action implied by this reason. */
    public CacheAction action() {
        return action;
    }
}
