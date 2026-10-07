package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for the immutable resource record and its version history (#33).
 */
public class ArchivedResourceTest {

    private static final VirtualResourceRef REF = new VirtualResourceRef(
            BookmarkUri.of(ResourceScheme.FILE, "///tmp/readme.md"), VirtualResourceKind.FILE);
    private static final ResourceDigest V1 = ContentHasher.digest("v1".getBytes());
    private static final ResourceDigest V2 = ContentHasher.digest("v2".getBytes());

    // ── ResourceVersion ──

    @Test
    public void resourceVersion_storesSequenceDigestAndTimestamp() {
        ResourceVersion v = new ResourceVersion(1, V1, 1000L);
        assertEquals(1, v.sequence());
        assertEquals(V1, v.digest());
        assertEquals(1000L, v.observedAtMillis());
    }

    @Test
    public void resourceVersion_equality() {
        assertEquals(new ResourceVersion(1, V1, 500L), new ResourceVersion(1, V1, 500L));
        assertEquals(new ResourceVersion(1, V1, 500L).hashCode(), new ResourceVersion(1, V1, 500L).hashCode());
        assertNotEquals(new ResourceVersion(1, V1, 500L), new ResourceVersion(2, V1, 500L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void resourceVersion_rejectsNullDigest() {
        new ResourceVersion(1, null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resourceVersion_rejectsSequenceBelowOne() {
        new ResourceVersion(0, V1, 0);
    }

    // ── Observations and version history ──

    @Test
    public void firstObservation_createsVersionOne_withoutIndexOrRemovalFacts() {
        ArchivedResource record = ArchivedResource.firstObservation(REF, V1, 100L);

        assertEquals(REF, record.ref());
        assertEquals(Collections.singletonList(new ResourceVersion(1, V1, 100L)), record.versions());
        assertEquals(new ResourceVersion(1, V1, 100L), record.latestObservedVersion());
        assertFalse(record.isIndexed());
        assertNull(record.indexedVersion());
        assertFalse(record.isRemovedAtSource());
        assertNull(record.sourceRemoval());
    }

    @Test
    public void identicalDigest_createsNoNewVersion() {
        ArchivedResource record = ArchivedResource.firstObservation(REF, V1, 100L);

        ArchivedResource again = record.observe(ContentHasher.digest("v1".getBytes()), 200L);

        assertSame(record, again);
        assertEquals(1, again.versions().size());
        assertEquals(100L, again.latestObservedVersion().observedAtMillis());
    }

    @Test
    public void changedDigest_appendsMonotoneNextVersion() {
        ArchivedResource record = ArchivedResource.firstObservation(REF, V1, 100L)
                .observe(V2, 200L)
                .observe(V1, 300L);

        assertEquals(Arrays.asList(
                new ResourceVersion(1, V1, 100L),
                new ResourceVersion(2, V2, 200L),
                new ResourceVersion(3, V1, 300L)), record.versions());
        assertEquals(new ResourceVersion(3, V1, 300L), record.latestObservedVersion());
    }

    @Test
    public void historyOrder_isDeterministic_andIndependentOfTimestamps() {
        ArchivedResource record = ArchivedResource.firstObservation(REF, V1, 900L).observe(V2, 100L);

        assertEquals(1, record.versions().get(0).sequence());
        assertEquals(2, record.versions().get(1).sequence());
        assertEquals(V2, record.latestObservedVersion().digest());
    }

    @Test
    public void transitions_leaveTheOriginalRecordUnchanged() {
        ArchivedResource original = ArchivedResource.firstObservation(REF, V1, 100L);

        original.observe(V2, 200L);
        original.markIndexed(original.latestObservedVersion(), 300L);
        original.markRemovedAtSource(400L);

        assertEquals(1, original.versions().size());
        assertFalse(original.isIndexed());
        assertFalse(original.isRemovedAtSource());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void versions_areUnmodifiable() {
        ArchivedResource.firstObservation(REF, V1, 100L).versions().add(new ResourceVersion(2, V2, 200L));
    }

    @Test
    public void constructor_copiesTheGivenHistory() {
        List<ResourceVersion> versions = new ArrayList<ResourceVersion>();
        versions.add(new ResourceVersion(1, V1, 100L));
        ArchivedResource record = new ArchivedResource(REF, versions, null, null);

        versions.add(new ResourceVersion(2, V2, 200L));

        assertEquals(1, record.versions().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsEmptyHistory() {
        new ArchivedResource(REF, Collections.<ResourceVersion>emptyList(), null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsSequenceGaps() {
        new ArchivedResource(REF, Arrays.asList(new ResourceVersion(1, V1, 1L), new ResourceVersion(3, V2, 2L)),
                null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsNullRef() {
        ArchivedResource.firstObservation(null, V1, 0L);
    }

    // ── Indexed-version fact ──

    @Test
    public void markIndexed_recordsVersionAndTime() {
        ArchivedResource record = ArchivedResource.firstObservation(REF, V1, 100L);

        ArchivedResource indexed = record.markIndexed(record.latestObservedVersion(), 150L);

        assertTrue(indexed.isIndexed());
        assertEquals(new IndexedVersion(new ResourceVersion(1, V1, 100L), 150L), indexed.indexedVersion());
    }

    @Test
    public void newObservation_keepsTheIndexedFactOnTheOlderVersion() {
        ArchivedResource record = ArchivedResource.firstObservation(REF, V1, 100L);
        record = record.markIndexed(record.latestObservedVersion(), 150L).observe(V2, 200L);

        assertEquals(2, record.latestObservedVersion().sequence());
        assertEquals(1, record.indexedVersion().version().sequence());
    }

    @Test
    public void withdrawIndexedVersion_keepsHistory() {
        ArchivedResource record = ArchivedResource.firstObservation(REF, V1, 100L).observe(V2, 200L);
        record = record.markIndexed(record.latestObservedVersion(), 250L);

        ArchivedResource withdrawn = record.withdrawIndexedVersion();

        assertFalse(withdrawn.isIndexed());
        assertEquals(record.versions(), withdrawn.versions());
    }

    @Test(expected = IllegalArgumentException.class)
    public void markIndexed_rejectsAVersionOutsideTheHistory() {
        ArchivedResource.firstObservation(REF, V1, 100L).markIndexed(new ResourceVersion(1, V2, 100L), 150L);
    }

    // ── Removal at source (tombstone) ──

    @Test
    public void markRemovedAtSource_keepsHistoryAndIndexedFact() {
        ArchivedResource record = ArchivedResource.firstObservation(REF, V1, 100L).observe(V2, 200L);
        record = record.markIndexed(record.latestObservedVersion(), 250L);

        ArchivedResource removed = record.markRemovedAtSource(300L);

        assertTrue(removed.isRemovedAtSource());
        assertEquals(new SourceRemoval(300L), removed.sourceRemoval());
        assertEquals(record.versions(), removed.versions());
        assertEquals("removal at source and indexed fact are independent",
                record.indexedVersion(), removed.indexedVersion());
        assertEquals(removed.latestObservedVersion(), removed.indexedVersion().version());
    }

    @Test
    public void markRemovedAtSource_keepsTheFirstRemovalObservation() {
        ArchivedResource removed = ArchivedResource.firstObservation(REF, V1, 100L).markRemovedAtSource(300L);

        assertSame(removed, removed.markRemovedAtSource(400L));
        assertEquals(300L, removed.sourceRemoval().observedAtMillis());
    }

    @Test
    public void observationAfterRemoval_clearsTheRemoval_withoutANewVersionForTheSameDigest() {
        ArchivedResource removed = ArchivedResource.firstObservation(REF, V1, 100L).markRemovedAtSource(300L);

        ArchivedResource seenAgain = removed.observe(V1, 400L);

        assertFalse(seenAgain.isRemovedAtSource());
        assertEquals(1, seenAgain.versions().size());
    }

    @Test
    public void equality_coversAllFacts() {
        ArchivedResource a = ArchivedResource.firstObservation(REF, V1, 100L);
        ArchivedResource b = ArchivedResource.firstObservation(REF, V1, 100L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, a.markRemovedAtSource(1L));
        assertNotEquals(a, a.markIndexed(a.latestObservedVersion(), 1L));
    }
}
