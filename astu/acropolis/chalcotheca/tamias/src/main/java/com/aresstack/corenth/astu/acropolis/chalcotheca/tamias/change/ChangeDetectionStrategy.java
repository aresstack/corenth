package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

/**
 * Decides how a resource's content relates to its Chalcotheca record.
 *
 * <p>A strategy is a pure function of its two inputs: it reads no archive, keeps no state and
 * causes no side effects. The orchestration (#10) reads the record, projects it into
 * {@link ResourceRecordFacts} and passes it together with the current observation.
 *
 * <p>{@link DigestChangeDetection} is the only strategy today. A metadata (mtime/size) strategy as
 * in MainframeMate needs recorded source metadata, which the #33 record does not hold yet.
 */
public interface ChangeDetectionStrategy {

    /**
     * @param record      the record facts, or {@code null} if no record exists for the resource
     * @param observation the current observation at the source, never {@code null}
     * @return the change decision
     */
    ChangeDecision detect(ResourceRecordFacts record, SourceObservation observation);
}
