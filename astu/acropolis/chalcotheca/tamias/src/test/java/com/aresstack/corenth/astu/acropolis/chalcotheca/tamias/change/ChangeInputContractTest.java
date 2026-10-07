package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChangeInputContractTest {

    @Test
    public void recordFacts_keepLatestObservedAndIndexedApart() {
        ResourceRecordFacts lagging = new ResourceRecordFacts(5, 3, false);

        assertTrue(lagging.isIndexed());
        assertFalse(lagging.isLatestObservedVersionIndexed());
        assertEquals(5, lagging.latestObservedSequence());
        assertEquals(3, lagging.indexedSequence());
    }

    @Test
    public void recordFacts_withoutIndexedFact() {
        ResourceRecordFacts unindexed = new ResourceRecordFacts(1, ResourceRecordFacts.NOT_INDEXED, false);

        assertFalse(unindexed.isIndexed());
        assertFalse(unindexed.isLatestObservedVersionIndexed());
    }

    @Test
    public void recordFacts_tombstoneIsIndependentOfIndexedFact() {
        ResourceRecordFacts removedButIndexed = new ResourceRecordFacts(7, 7, true);

        assertTrue(removedButIndexed.isRemovedAtSource());
        assertTrue(removedButIndexed.isLatestObservedVersionIndexed());
    }

    @Test
    public void recordFacts_areValueObjects() {
        assertEquals(new ResourceRecordFacts(2, 1, true), new ResourceRecordFacts(2, 1, true));
        assertEquals(new ResourceRecordFacts(2, 1, true).hashCode(), new ResourceRecordFacts(2, 1, true).hashCode());
        assertFalse(new ResourceRecordFacts(2, 1, true).equals(new ResourceRecordFacts(2, 2, true)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void recordFacts_rejectIndexedNewerThanLatest() {
        new ResourceRecordFacts(2, 3, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void recordFacts_rejectEmptyHistory() {
        new ResourceRecordFacts(0, ResourceRecordFacts.NOT_INDEXED, false);
    }

    @Test
    public void observation_absentCarriesNoComparison() {
        assertFalse(SourceObservation.absent().isPresent());
        assertNull(SourceObservation.absent().comparison());
    }

    @Test
    public void observation_presentCarriesComparison() {
        SourceObservation same = SourceObservation.present(ContentComparison.SAME_AS_LATEST_OBSERVED);

        assertTrue(same.isPresent());
        assertEquals(ContentComparison.SAME_AS_LATEST_OBSERVED, same.comparison());
        assertEquals(ContentComparison.NOT_COMPARED, SourceObservation.presentWithoutRecord().comparison());
    }

    @Test(expected = IllegalArgumentException.class)
    public void changeDecision_rejectsReasonThatContradictsObservation() {
        new ChangeDecision(ChangeReasonCode.DIGEST_UNCHANGED, new ResourceRecordFacts(1, 1, false), SourceObservation.absent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void changeDecision_rejectsRecordForFirstSighting() {
        new ChangeDecision(ChangeReasonCode.FIRST_SEEN, new ResourceRecordFacts(1, 1, false),
                SourceObservation.presentWithoutRecord());
    }

    @Test(expected = IllegalArgumentException.class)
    public void changeDecision_requiresRecordForRemoval() {
        new ChangeDecision(ChangeReasonCode.REMOVAL_OBSERVED, null, SourceObservation.absent());
    }
}
