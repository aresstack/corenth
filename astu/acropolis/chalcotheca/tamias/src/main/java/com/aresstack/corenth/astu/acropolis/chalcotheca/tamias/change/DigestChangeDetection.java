package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

/**
 * Digest-based change detection against the latest observed version of the record.
 *
 * <p>The digest comparison itself is done by the orchestration (#10) with the canonical
 * Chalcotheca {@code ResourceDigest} equality and arrives as {@link ContentComparison}; this class
 * turns it, the record facts and the presence at the source into the change outcome.
 *
 * <p>The comparison is against the latest <em>observed</em> version, never the indexed one:
 * whether the index lags behind is an independent fact that the derivative disposition decides
 * on. This mirrors Chalcotheca's own rule that an identical digest creates no new version and a
 * different digest creates version {@code n + 1}.
 *
 * <ul>
 *   <li>no record, present: {@code FIRST_SEEN};</li>
 *   <li>no record, absent: {@code NOT_FOUND_AT_SOURCE};</li>
 *   <li>record not removed, same digest: {@code DIGEST_UNCHANGED};</li>
 *   <li>record not removed, other digest: {@code DIGEST_CHANGED};</li>
 *   <li>record removed, same digest: {@code REAPPEARED_UNCHANGED};</li>
 *   <li>record removed, other digest: {@code REAPPEARED_CHANGED};</li>
 *   <li>record not removed, absent: {@code REMOVAL_OBSERVED};</li>
 *   <li>record removed, absent: {@code REMOVAL_ALREADY_RECORDED}.</li>
 * </ul>
 */
public final class DigestChangeDetection implements ChangeDetectionStrategy {

    @Override
    public ChangeDecision detect(ResourceRecordFacts record, SourceObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation must not be null");
        }
        if (record == null) {
            if (observation.isPresent() && observation.comparison() != ContentComparison.NOT_COMPARED) {
                throw new IllegalArgumentException("a resource without record cannot be compared, got "
                        + observation.comparison());
            }
            return new ChangeDecision(observation.isPresent()
                    ? ChangeReasonCode.FIRST_SEEN
                    : ChangeReasonCode.NOT_FOUND_AT_SOURCE, null, observation);
        }
        if (!observation.isPresent()) {
            return new ChangeDecision(record.isRemovedAtSource()
                    ? ChangeReasonCode.REMOVAL_ALREADY_RECORDED
                    : ChangeReasonCode.REMOVAL_OBSERVED, record, observation);
        }
        if (observation.comparison() == ContentComparison.NOT_COMPARED) {
            throw new IllegalArgumentException("a present observation of a recorded resource must carry a comparison result");
        }
        boolean sameContent = observation.comparison() == ContentComparison.SAME_AS_LATEST_OBSERVED;
        ChangeReasonCode reason;
        if (record.isRemovedAtSource()) {
            reason = sameContent ? ChangeReasonCode.REAPPEARED_UNCHANGED : ChangeReasonCode.REAPPEARED_CHANGED;
        } else {
            reason = sameContent ? ChangeReasonCode.DIGEST_UNCHANGED : ChangeReasonCode.DIGEST_CHANGED;
        }
        return new ChangeDecision(reason, record, observation);
    }
}
