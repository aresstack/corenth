package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition;

import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ChangeDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ContentComparison;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.DigestChangeDetection;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ResourceRecordFacts;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.SourceObservation;
import org.junit.Test;

import static com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ResourceRecordFacts.NOT_INDEXED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DerivativeDispositionPolicyTest {

    private final DigestChangeDetection detection = new DigestChangeDetection();
    private final DerivativeDispositionPolicy policy = new DerivativeDispositionPolicy();

    private DerivativeDisposition decide(ResourceRecordFacts record, SourceObservation observation) {
        return policy.decide(detection.detect(record, observation));
    }

    private static SourceObservation same() {
        return SourceObservation.present(ContentComparison.SAME_AS_LATEST_OBSERVED);
    }

    private static SourceObservation changed() {
        return SourceObservation.present(ContentComparison.DIFFERS_FROM_LATEST_OBSERVED);
    }

    @Test
    public void unknownResourceObserved_requiresIndexing() {
        DerivativeDisposition disposition = decide(null, SourceObservation.presentWithoutRecord());

        assertEquals(IndexReasonCode.NOT_YET_INDEXED, disposition.indexReason());
        assertEquals(IndexAction.INDEX, disposition.indexAction());
        assertTrue(disposition.requiresIndexing());
        assertEquals(CacheReasonCode.NO_RECORDED_VERSION, disposition.cacheReason());
        assertEquals(CacheAction.REFRESH, disposition.cacheAction());
    }

    @Test
    public void sameObservedAndSameIndexedVersion_retainsWithoutReindex() {
        DerivativeDisposition disposition = decide(new ResourceRecordFacts(4, 4, false), same());

        assertEquals(IndexReasonCode.INDEXED_VERSION_CURRENT, disposition.indexReason());
        assertEquals(IndexAction.RETAIN, disposition.indexAction());
        assertFalse(disposition.requiresIndexing());
        assertFalse(disposition.requiresWithdrawal());
        assertEquals(CacheAction.RETAIN, disposition.cacheAction());
    }

    @Test
    public void sameObservedVersionWithoutIndexedFact_requiresReindexAlthoughContentIsUnchanged() {
        DerivativeDisposition disposition = decide(new ResourceRecordFacts(4, NOT_INDEXED, false), same());

        assertEquals("content is unchanged", ContentComparison.SAME_AS_LATEST_OBSERVED,
                disposition.change().observation().comparison());
        assertEquals(IndexReasonCode.INDEXED_FACT_MISSING, disposition.indexReason());
        assertEquals(IndexAction.REINDEX, disposition.indexAction());
        assertTrue(disposition.requiresIndexing());
        assertEquals("unchanged content keeps its caches", CacheAction.RETAIN, disposition.cacheAction());
    }

    @Test
    public void latestObservedNewerThanIndexed_requiresReindex() {
        DerivativeDisposition disposition = decide(new ResourceRecordFacts(5, 3, false), same());

        assertEquals(IndexReasonCode.INDEXED_VERSION_OUTDATED, disposition.indexReason());
        assertEquals(IndexAction.REINDEX, disposition.indexAction());
        assertEquals(CacheAction.RETAIN, disposition.cacheAction());
    }

    @Test
    public void changedDigest_refreshesCachesAndReindexes() {
        DerivativeDisposition indexed = decide(new ResourceRecordFacts(2, 2, false), changed());
        DerivativeDisposition unindexed = decide(new ResourceRecordFacts(2, NOT_INDEXED, false), changed());

        for (DerivativeDisposition disposition : new DerivativeDisposition[] {indexed, unindexed}) {
            assertEquals(CacheReasonCode.CONTENT_CHANGED, disposition.cacheReason());
            assertEquals(CacheAction.REFRESH, disposition.cacheAction());
            assertEquals(IndexReasonCode.CONTENT_CHANGED, disposition.indexReason());
            assertEquals(IndexAction.REINDEX, disposition.indexAction());
        }
    }

    @Test
    public void tombstonedAndIndexed_requiresWithdrawal() {
        DerivativeDisposition observedNow = decide(new ResourceRecordFacts(7, 7, false), SourceObservation.absent());
        DerivativeDisposition alreadyTombstoned = decide(new ResourceRecordFacts(7, 7, true), SourceObservation.absent());

        for (DerivativeDisposition disposition : new DerivativeDisposition[] {observedNow, alreadyTombstoned}) {
            assertEquals(IndexReasonCode.REMOVED_WHILE_INDEXED, disposition.indexReason());
            assertEquals(IndexAction.WITHDRAW, disposition.indexAction());
            assertTrue(disposition.requiresWithdrawal());
            assertEquals(CacheAction.INVALIDATE, disposition.cacheAction());
        }
    }

    @Test
    public void tombstonedAndNotIndexed_requiresNoWithdrawal() {
        DerivativeDisposition disposition = decide(new ResourceRecordFacts(3, NOT_INDEXED, true), SourceObservation.absent());

        assertEquals(IndexReasonCode.REMOVED_NOT_INDEXED, disposition.indexReason());
        assertEquals(IndexAction.NONE, disposition.indexAction());
        assertFalse(disposition.requiresWithdrawal());
        assertFalse(disposition.requiresIndexing());
        assertEquals(CacheReasonCode.REMOVED_AT_SOURCE, disposition.cacheReason());
    }

    @Test
    public void reappearedUnchangedAfterTombstone_followsTheIndexedFact() {
        assertEquals(IndexAction.RETAIN, decide(new ResourceRecordFacts(2, 2, true), same()).indexAction());
        assertEquals(IndexAction.REINDEX, decide(new ResourceRecordFacts(2, NOT_INDEXED, true), same()).indexAction());
        assertEquals(IndexAction.REINDEX, decide(new ResourceRecordFacts(2, 2, true), changed()).indexAction());
    }

    @Test
    public void unknownAndAbsent_requiresNothingButDroppingStrayCaches() {
        DerivativeDisposition disposition = decide(null, SourceObservation.absent());

        assertEquals(IndexAction.NONE, disposition.indexAction());
        assertEquals(CacheAction.INVALIDATE, disposition.cacheAction());
        assertEquals(IndexReasonCode.NOT_FOUND, disposition.indexReason());
    }

    @Test
    public void reasonCodes_areStableAndImplyTheirActions() {
        assertEquals("NOT_YET_INDEXED, INDEXED_VERSION_CURRENT, INDEXED_FACT_MISSING, INDEXED_VERSION_OUTDATED, "
                        + "CONTENT_CHANGED, REMOVED_WHILE_INDEXED, REMOVED_NOT_INDEXED, NOT_FOUND",
                java.util.Arrays.toString(IndexReasonCode.values()).replace("[", "").replace("]", ""));
        assertEquals("NO_RECORDED_VERSION, CONTENT_UNCHANGED, CONTENT_CHANGED, REMOVED_AT_SOURCE, NOT_FOUND_AT_SOURCE",
                java.util.Arrays.toString(CacheReasonCode.values()).replace("[", "").replace("]", ""));
        assertEquals(IndexAction.WITHDRAW, IndexReasonCode.REMOVED_WHILE_INDEXED.action());
        assertEquals(CacheAction.INVALIDATE, CacheReasonCode.REMOVED_AT_SOURCE.action());
    }

    @Test
    public void dispositions_areValueObjects() {
        ChangeDecision change = detection.detect(new ResourceRecordFacts(1, 1, false), same());

        assertEquals(policy.decide(change), policy.decide(change));
        assertEquals(policy.decide(change).hashCode(), policy.decide(change).hashCode());
        assertNotEquals(policy.decide(change), decide(new ResourceRecordFacts(1, NOT_INDEXED, false), same()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingChange_isRejected() {
        policy.decide(null);
    }
}
