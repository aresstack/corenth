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
    private final String rejection;
    private final CacheReasonCode cacheReason;
    private final IndexReasonCode indexReason;

    public DerivativeDisposition(ChangeDecision change, CacheReasonCode cacheReason, IndexReasonCode indexReason) {
        this(requireChange(change), null, cacheReason, indexReason);
    }

    private static ChangeDecision requireChange(ChangeDecision change) {
        if (change == null) {
            throw new IllegalArgumentException("change must not be null");
        }
        return change;
    }

    /** Creates a disposition for a resource the policies do not admit; see {@link DerivativeDispositionPolicy}. */
    static DerivativeDisposition notAdmitted(String rejection, CacheReasonCode cacheReason, IndexReasonCode indexReason) {
        if (rejection == null || rejection.isEmpty()) {
            throw new IllegalArgumentException("rejection must not be empty");
        }
        return new DerivativeDisposition(null, rejection, cacheReason, indexReason);
    }

    private DerivativeDisposition(ChangeDecision change, String rejection,
                                  CacheReasonCode cacheReason, IndexReasonCode indexReason) {
        if (cacheReason == null) {
            throw new IllegalArgumentException("cacheReason must not be null");
        }
        if (indexReason == null) {
            throw new IllegalArgumentException("indexReason must not be null");
        }
        this.change = change;
        this.rejection = rejection;
        this.cacheReason = cacheReason;
        this.indexReason = indexReason;
    }

    /** Return the change decision this disposition was derived from. */
    /** Return the change decision this disposition derives from, or {@code null} for a rejected admission. */
    public ChangeDecision change() {
        return change;
    }

    /** Return whether this disposition derives from a rejected admission rather than a content change. */
    public boolean isNotAdmitted() {
        return rejection != null;
    }

    /** Return the policy explanation of a rejected admission, or {@code null}. */
    public String rejection() {
        return rejection;
    }

    /** Return the reason this disposition derives from: the change reason code or the rejection. */
    public String trigger() {
        return change != null ? change.reasonCode().name() : "NOT_ADMITTED (" + rejection + ")";
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
        return "DerivativeDisposition{trigger=" + trigger()
                + ", cache=" + cacheAction() + "/" + cacheReason
                + ", index=" + indexAction() + "/" + indexReason + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DerivativeDisposition)) return false;
        DerivativeDisposition that = (DerivativeDisposition) o;
        return cacheReason == that.cacheReason && indexReason == that.indexReason
                && (change == null ? that.change == null : change.equals(that.change))
                && (rejection == null ? that.rejection == null : rejection.equals(that.rejection));
    }

    @Override
    public int hashCode() {
        int result = change != null ? change.hashCode() : rejection.hashCode();
        result = 31 * result + cacheReason.hashCode();
        return 31 * result + indexReason.hashCode();
    }
}
