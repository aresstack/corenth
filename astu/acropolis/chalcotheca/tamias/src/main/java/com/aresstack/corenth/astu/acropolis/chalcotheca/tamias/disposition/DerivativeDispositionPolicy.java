package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition;

import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AcceptanceDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PolicyReason;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ChangeDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ResourceRecordFacts;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope.ScopeDecision;

/**
 * Maps a {@link ChangeDecision} and the record facts it carries to a {@link DerivativeDisposition}.
 *
 * <p>Semantics (the cache part depends on the change only, the index part also on the
 * indexed-version fact):
 * <ul>
 *   <li>{@code NEW}: cache {@code REFRESH/NO_RECORDED_VERSION}, index {@code INDEX/NOT_YET_INDEXED}.</li>
 *   <li>{@code UNCHANGED}: cache {@code RETAIN/CONTENT_UNCHANGED}; index
 *       {@code RETAIN/INDEXED_VERSION_CURRENT} if the latest observed version is indexed,
 *       {@code REINDEX/INDEXED_VERSION_OUTDATED} if an older version is indexed,
 *       {@code REINDEX/INDEXED_FACT_MISSING} if nothing is indexed.</li>
 *   <li>{@code CHANGED}: cache {@code REFRESH/CONTENT_CHANGED}, index {@code REINDEX/CONTENT_CHANGED}.</li>
 *   <li>{@code REMOVED}: cache {@code INVALIDATE/REMOVED_AT_SOURCE}; index
 *       {@code WITHDRAW/REMOVED_WHILE_INDEXED} if a version is indexed, otherwise
 *       {@code NONE/REMOVED_NOT_INDEXED}.</li>
 *   <li>{@code NOT_FOUND}: cache {@code INVALIDATE/NOT_FOUND_AT_SOURCE}, index {@code NONE/NOT_FOUND}.</li>
 * </ul>
 * A resource the indexing, scope or size policy does not admit is decided by
 * {@link #decideNotAdmitted(ResourceRecordFacts, PolicyReason)} and
 * {@link #decideNotAdmitted(ResourceRecordFacts, ScopeDecision)}: cache {@code RETAIN/NOT_ADMITTED};
 * index {@code WITHDRAW/NOT_ADMITTED_WHILE_INDEXED} if a version is indexed,
 * {@code NONE/NOT_ADMITTED_NOT_INDEXED} if the record holds none, and
 * {@code WITHDRAW/NOT_ADMITTED_UNRECORDED} without a record. An actor- or request-specific access
 * decision is no admission decision and never reaches this mapping.
 *
 * <p>A reappearance after a tombstone is treated by its content kind ({@code UNCHANGED} or
 * {@code CHANGED}); clearing the tombstone is Chalcotheca's {@code observe} transition.
 *
 * <p>Pure and stateless; no cache presence, no persistence, no execution.
 */
public final class DerivativeDispositionPolicy {

    /**
     * @param change the change decision, carrying the record facts it was made against
     * @return the disposition for caches and index entries
     */
    public DerivativeDisposition decide(ChangeDecision change) {
        if (change == null) {
            throw new IllegalArgumentException("change must not be null");
        }
        ResourceRecordFacts record = change.record();
        switch (change.kind()) {
            case NEW:
                return new DerivativeDisposition(change, CacheReasonCode.NO_RECORDED_VERSION, IndexReasonCode.NOT_YET_INDEXED);
            case UNCHANGED:
                return new DerivativeDisposition(change, CacheReasonCode.CONTENT_UNCHANGED, indexReasonForUnchanged(record));
            case CHANGED:
                return new DerivativeDisposition(change, CacheReasonCode.CONTENT_CHANGED, IndexReasonCode.CONTENT_CHANGED);
            case REMOVED:
                return new DerivativeDisposition(change, CacheReasonCode.REMOVED_AT_SOURCE, record.isIndexed()
                        ? IndexReasonCode.REMOVED_WHILE_INDEXED
                        : IndexReasonCode.REMOVED_NOT_INDEXED);
            case NOT_FOUND:
                return new DerivativeDisposition(change, CacheReasonCode.NOT_FOUND_AT_SOURCE, IndexReasonCode.NOT_FOUND);
            default:
                throw new IllegalStateException("unhandled change kind " + change.kind());
        }
    }

    /**
     * Maps a resource-level indexing-policy rejection (scheme, include or exclude pattern) to a
     * disposition (#10 Slice 5).
     *
     * @param record  the record facts, or {@code null} if the resource has no record
     * @param verdict the indexing-policy outcome; must be {@code DENY}
     * @return the disposition for caches and index entries
     */
    public DerivativeDisposition decideNotAdmitted(ResourceRecordFacts record, PolicyReason verdict) {
        if (verdict == null || verdict.decision() != AcceptanceDecision.DENY) {
            throw new IllegalArgumentException("verdict must be a DENY");
        }
        return notAdmitted(record, verdict.reason());
    }

    /**
     * Maps a rejecting scope or size decision to a disposition (#10 Slice 5).
     *
     * @param record   the record facts, or {@code null} if the resource has no record
     * @param decision the scope or size decision; must be rejected
     * @return the disposition for caches and index entries
     */
    public DerivativeDisposition decideNotAdmitted(ResourceRecordFacts record, ScopeDecision decision) {
        if (decision == null || !decision.isRejected()) {
            throw new IllegalArgumentException("decision must be rejected");
        }
        return notAdmitted(record, decision.reasonCode() + ": " + decision.explanation());
    }

    private static DerivativeDisposition notAdmitted(ResourceRecordFacts record, String rejection) {
        IndexReasonCode index = record == null ? IndexReasonCode.NOT_ADMITTED_UNRECORDED
                : record.isIndexed() ? IndexReasonCode.NOT_ADMITTED_WHILE_INDEXED
                : IndexReasonCode.NOT_ADMITTED_NOT_INDEXED;
        return DerivativeDisposition.notAdmitted(rejection, CacheReasonCode.NOT_ADMITTED, index);
    }

    private static IndexReasonCode indexReasonForUnchanged(ResourceRecordFacts record) {
        if (!record.isIndexed()) {
            return IndexReasonCode.INDEXED_FACT_MISSING;
        }
        return record.isLatestObservedVersionIndexed()
                ? IndexReasonCode.INDEXED_VERSION_CURRENT
                : IndexReasonCode.INDEXED_VERSION_OUTDATED;
    }
}
