package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition;

import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ChangeDecision;

/**
 * The immutable decision what should happen to a resource's derivatives: its transient payload
 * caches and its index entries. Both parts are decided independently, because content change and
 * indexing state are orthogonal (unchanged content can still require a reindex).
 *
 * <p>Created by {@link DerivativeDispositionPolicy}. The disposition is a plan for the
 * orchestration (#10); nothing is executed or stored here.
 */
public final class DerivativeDisposition {

    private final ChangeDecision change;
    private final CacheReasonCode cacheReason;
    private final IndexReasonCode indexReason;

    public DerivativeDisposition(ChangeDecision change, CacheReasonCode cacheReason, IndexReasonCode indexReason) {
        if (change == null) {
            throw new IllegalArgumentException("change must not be null");
        }
        if (cacheReason == null) {
            throw new IllegalArgumentException("cacheReason must not be null");
        }
        if (indexReason == null) {
            throw new IllegalArgumentException("indexReason must not be null");
        }
        this.change = change;
        this.cacheReason = cacheReason;
        this.indexReason = indexReason;
    }

    /** Return the change decision this disposition was derived from. */
    public ChangeDecision change() {
        return change;
    }

    /** Return the machine-readable reason for the cache action. */
    public CacheReasonCode cacheReason() {
        return cacheReason;
    }

    /** Return what to do with cached derivatives. */
    public CacheAction cacheAction() {
        return cacheReason.action();
    }

    /** Return the machine-readable reason for the index action. */
    public IndexReasonCode indexReason() {
        return indexReason;
    }

    /** Return what to do with index entries. */
    public IndexAction indexAction() {
        return indexReason.action();
    }

    /** Return whether index entries have to be built or rebuilt ({@code INDEX} or {@code REINDEX}). */
    public boolean requiresIndexing() {
        return indexAction() == IndexAction.INDEX || indexAction() == IndexAction.REINDEX;
    }

    /** Return whether index entries have to be withdrawn. */
    public boolean requiresWithdrawal() {
        return indexAction() == IndexAction.WITHDRAW;
    }

    @Override
    public String toString() {
        return "DerivativeDisposition{change=" + change.reasonCode()
                + ", cache=" + cacheAction() + "/" + cacheReason
                + ", index=" + indexAction() + "/" + indexReason + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DerivativeDisposition)) return false;
        DerivativeDisposition that = (DerivativeDisposition) o;
        return cacheReason == that.cacheReason && indexReason == that.indexReason && change.equals(that.change);
    }

    @Override
    public int hashCode() {
        int result = change.hashCode();
        result = 31 * result + cacheReason.hashCode();
        return 31 * result + indexReason.hashCode();
    }
}
