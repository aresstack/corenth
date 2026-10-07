package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

import org.junit.Test;

import static com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ContentComparison.DIFFERS_FROM_LATEST_OBSERVED;
import static com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ContentComparison.NOT_COMPARED;
import static com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ContentComparison.SAME_AS_LATEST_OBSERVED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class DigestChangeDetectionTest {

    private final ChangeDetectionStrategy detection = new DigestChangeDetection();

    private static ResourceRecordFacts record(long latest, long indexed, boolean removed) {
        return new ResourceRecordFacts(latest, indexed, removed);
    }

    @Test
    public void firstSeen_isNew() {
        ChangeDecision decision = detection.detect(null, SourceObservation.presentWithoutRecord());

        assertEquals(ChangeReasonCode.FIRST_SEEN, decision.reasonCode());
        assertEquals(ChangeKind.NEW, decision.kind());
        assertNull(decision.record());
    }

    @Test
    public void unknownAndAbsent_isNotFound() {
        assertEquals(ChangeKind.NOT_FOUND, detection.detect(null, SourceObservation.absent()).kind());
    }

    @Test
    public void identicalDigest_isUnchanged() {
        ResourceRecordFacts facts = record(3, 3, false);

        ChangeDecision decision = detection.detect(facts, SourceObservation.present(SAME_AS_LATEST_OBSERVED));

        assertEquals(ChangeReasonCode.DIGEST_UNCHANGED, decision.reasonCode());
        assertEquals(ChangeKind.UNCHANGED, decision.kind());
        assertSame(facts, decision.record());
    }

    @Test
    public void identicalDigest_isUnchangedEvenWithoutIndexedFact() {
        assertEquals(ChangeKind.UNCHANGED,
                detection.detect(record(3, ResourceRecordFacts.NOT_INDEXED, false),
                        SourceObservation.present(SAME_AS_LATEST_OBSERVED)).kind());
    }

    @Test
    public void identicalToLatestObserved_isUnchangedEvenIfIndexedVersionIsOlder() {
        assertEquals(ChangeKind.UNCHANGED,
                detection.detect(record(4, 2, false), SourceObservation.present(SAME_AS_LATEST_OBSERVED)).kind());
    }

    @Test
    public void changedDigest_isChanged() {
        ChangeDecision decision = detection.detect(record(3, 3, false), SourceObservation.present(DIFFERS_FROM_LATEST_OBSERVED));

        assertEquals(ChangeReasonCode.DIGEST_CHANGED, decision.reasonCode());
        assertEquals(ChangeKind.CHANGED, decision.kind());
    }

    @Test
    public void absentWhileRecorded_isRemoved() {
        ChangeDecision decision = detection.detect(record(2, 2, false), SourceObservation.absent());

        assertEquals(ChangeReasonCode.REMOVAL_OBSERVED, decision.reasonCode());
        assertEquals(ChangeKind.REMOVED, decision.kind());
    }

    @Test
    public void absentWhileAlreadyTombstoned_isRemovedAndReportsTheExistingTombstone() {
        ChangeDecision decision = detection.detect(record(2, 2, true), SourceObservation.absent());

        assertEquals(ChangeReasonCode.REMOVAL_ALREADY_RECORDED, decision.reasonCode());
        assertEquals(ChangeKind.REMOVED, decision.kind());
    }

    @Test
    public void reappearanceAfterTombstone_isClassifiedByContent() {
        assertEquals(ChangeReasonCode.REAPPEARED_UNCHANGED,
                detection.detect(record(2, 2, true), SourceObservation.present(SAME_AS_LATEST_OBSERVED)).reasonCode());
        assertEquals(ChangeReasonCode.REAPPEARED_CHANGED,
                detection.detect(record(2, 2, true), SourceObservation.present(DIFFERS_FROM_LATEST_OBSERVED)).reasonCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void recordedResourceWithoutComparison_isRejected() {
        detection.detect(record(1, 1, false), SourceObservation.present(NOT_COMPARED));
    }

    @Test(expected = IllegalArgumentException.class)
    public void comparisonWithoutRecord_isRejected() {
        detection.detect(null, SourceObservation.present(SAME_AS_LATEST_OBSERVED));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingObservation_isRejected() {
        detection.detect(null, null);
    }

    @Test
    public void everyReasonCode_impliesExactlyOneKind() {
        assertEquals("FIRST_SEEN, DIGEST_UNCHANGED, REAPPEARED_UNCHANGED, DIGEST_CHANGED, REAPPEARED_CHANGED, "
                        + "REMOVAL_OBSERVED, REMOVAL_ALREADY_RECORDED, NOT_FOUND_AT_SOURCE",
                java.util.Arrays.toString(ChangeReasonCode.values()).replace("[", "").replace("]", ""));
        assertEquals(ChangeKind.NEW, ChangeReasonCode.FIRST_SEEN.kind());
        assertEquals(ChangeKind.UNCHANGED, ChangeReasonCode.REAPPEARED_UNCHANGED.kind());
        assertEquals(ChangeKind.CHANGED, ChangeReasonCode.REAPPEARED_CHANGED.kind());
        assertEquals(ChangeKind.REMOVED, ChangeReasonCode.REMOVAL_ALREADY_RECORDED.kind());
        assertEquals(ChangeKind.NOT_FOUND, ChangeReasonCode.NOT_FOUND_AT_SOURCE.kind());
    }
}
