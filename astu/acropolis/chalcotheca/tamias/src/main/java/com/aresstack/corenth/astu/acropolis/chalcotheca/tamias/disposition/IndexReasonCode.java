package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition;

/**
 * Stable, machine-readable reasons for the index part of a {@link DerivativeDisposition}. Each
 * code implies exactly one {@link IndexAction}.
 */
public enum IndexReasonCode {
    /** First sighting; no index entries exist yet. */
    NOT_YET_INDEXED(IndexAction.INDEX),
    /** The indexed version is the latest observed version and the content is unchanged. */
    INDEXED_VERSION_CURRENT(IndexAction.RETAIN),
    /** The content is unchanged but no version is recorded as indexed (withdrawn or never indexed). */
    INDEXED_FACT_MISSING(IndexAction.REINDEX),
    /** The content is unchanged but the indexed version is older than the latest observed version. */
    INDEXED_VERSION_OUTDATED(IndexAction.REINDEX),
    /** The content changed, so any indexed version is outdated. */
    CONTENT_CHANGED(IndexAction.REINDEX),
    /** The resource is gone at its source while a version is still recorded as indexed. */
    REMOVED_WHILE_INDEXED(IndexAction.WITHDRAW),
    /** The resource is gone at its source and nothing is recorded as indexed. */
    REMOVED_NOT_INDEXED(IndexAction.NONE),
    /** The resource is neither recorded nor present; nothing to index or withdraw. */
    NOT_FOUND(IndexAction.NONE),
    /**
     * The indexing, scope or size policy no longer admits the resource while a version is
     * recorded as indexed (#10 Slice 5).
     */
    NOT_ADMITTED_WHILE_INDEXED(IndexAction.WITHDRAW),
    /** The policies do not admit the resource and its record holds no indexed version. */
    NOT_ADMITTED_NOT_INDEXED(IndexAction.NONE),
    /**
     * The policies do not admit the resource and no record exists, so an index entry from an
     * earlier, unrecorded run cannot be ruled out; withdrawing is idempotent.
     */
    NOT_ADMITTED_UNRECORDED(IndexAction.WITHDRAW);

    private final IndexAction action;

    IndexReasonCode(IndexAction action) {
        this.action = action;
    }

    /** Return the index action implied by this reason. */
    public IndexAction action() {
        return action;
    }
}
