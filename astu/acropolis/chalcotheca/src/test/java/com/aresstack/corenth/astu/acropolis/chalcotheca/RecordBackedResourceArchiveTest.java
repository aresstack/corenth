package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.*;

/**
 * Tests for the {@link ResourceArchive} compatibility facade over the resource records (#33).
 */
public class RecordBackedResourceArchiveTest {

    private static final long NOW = 5_000L;
    private static final BookmarkUri URI = BookmarkUri.of(ResourceScheme.FILE, "///tmp/readme.md");
    private static final VirtualResourceRef REF = new VirtualResourceRef(URI, VirtualResourceKind.FILE);
    private static final VirtualResourceRef SAME_URI_AS_DIRECTORY =
            new VirtualResourceRef(URI, VirtualResourceKind.DIRECTORY);
    private static final VirtualResourceRef OTHER = new VirtualResourceRef(
            BookmarkUri.of(ResourceScheme.FILE, "///tmp/other.md"), VirtualResourceKind.FILE);
    private static final ResourceDigest V1 = ContentHasher.digest("v1".getBytes());
    private static final ResourceDigest V2 = ContentHasher.digest("v2".getBytes());

    private InMemoryResourceArchiveRepository records;
    private RecordBackedResourceArchive archive;

    @Before
    public void setUp() {
        records = new InMemoryResourceArchiveRepository();
        archive = new RecordBackedResourceArchive(records,
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
    }

    // ── store ──

    @Test
    public void store_createsTheRecord_withVersionOneIndexed() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));

        ArchivedResource record = records.findByRef(REF);
        assertEquals(1, record.versions().size());
        assertEquals(new ResourceVersion(1, V1, 100L), record.latestObservedVersion());
        assertEquals(new IndexedVersion(record.latestObservedVersion(), 100L), record.indexedVersion());
    }

    @Test
    public void store_identicalDigest_createsNoNewVersion_butUpdatesTheIndexingTime() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.store(new ResourceSnapshot(REF, V1, 200L));

        ArchivedResource record = records.findByRef(REF);
        assertEquals(1, record.versions().size());
        assertEquals(200L, record.indexedVersion().indexedAtMillis());
        assertEquals(200L, archive.find(REF).indexedAtMillis());
    }

    @Test
    public void store_changedDigest_createsTheNextVersion_andIndexesIt() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.store(new ResourceSnapshot(REF, V2, 200L));

        ArchivedResource record = records.findByRef(REF);
        assertEquals(2, record.versions().size());
        assertEquals(new ResourceVersion(2, V2, 200L), record.latestObservedVersion());
        assertEquals(record.latestObservedVersion(), record.indexedVersion().version());
    }

    @Test
    public void store_afterRemovalAtSource_clearsTheRemoval() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.removeByUri(URI);

        archive.store(new ResourceSnapshot(REF, V1, 200L));

        assertFalse(records.findByRef(REF).isRemovedAtSource());
    }

    // ── find ──

    @Test
    public void find_returnsTheIndexedVersionAsSnapshot() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));

        ResourceSnapshot found = archive.find(REF);

        assertEquals(REF, found.ref());
        assertEquals(V1, found.digest());
        assertEquals(100L, found.indexedAtMillis());
    }

    @Test
    public void find_returnsNull_forUnknownOrNullRef() {
        assertNull(archive.find(REF));
        assertNull(archive.find(null));
    }

    @Test
    public void find_neverFallsBackToTheLatestObservedVersion() {
        records.save(ArchivedResource.firstObservation(REF, V1, 100L));

        assertNull(archive.find(REF));
        assertNull(archive.findByUri(URI));
    }

    @Test
    public void find_returnsTheIndexedVersion_evenIfANewerVersionWasObserved() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        records.save(records.findByRef(REF).observe(V2, 200L));

        assertEquals(V1, archive.find(REF).digest());
    }

    // ── hasChanged ──

    @Test
    public void hasChanged_isTrue_withoutAnyRecord() {
        assertTrue(archive.hasChanged(REF, V1));
    }

    @Test
    public void hasChanged_isTrue_withoutAnIndexedFact() {
        records.save(ArchivedResource.firstObservation(REF, V1, 100L));

        assertTrue(archive.hasChanged(REF, V1));
    }

    @Test
    public void hasChanged_isFalse_forTheIndexedDigest() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));

        assertFalse(archive.hasChanged(REF, ContentHasher.digest("v1".getBytes())));
    }

    @Test
    public void hasChanged_isTrue_forADifferentDigest() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));

        assertTrue(archive.hasChanged(REF, V2));
    }

    @Test
    public void hasChanged_comparesAgainstTheIndexedVersion_notTheLatestObserved() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        records.save(records.findByRef(REF).observe(V2, 200L));

        assertFalse(archive.hasChanged(REF, V1));
        assertTrue(archive.hasChanged(REF, V2));
    }

    // ── remove ──

    @Test
    public void remove_withdrawsTheIndexedFact_withoutHistoryLoss() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.store(new ResourceSnapshot(REF, V2, 200L));

        assertTrue(archive.remove(REF));

        assertNull(archive.find(REF));
        assertTrue("without an indexed fact the same digest counts as changed", archive.hasChanged(REF, V2));
        ArchivedResource record = records.findByRef(REF);
        assertNotNull("the record is kept", record);
        assertEquals(2, record.versions().size());
        assertFalse(record.isIndexed());
    }

    @Test
    public void remove_returnsFalse_whenNothingIsIndexed() {
        assertFalse(archive.remove(REF));
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        assertTrue(archive.remove(REF));
        assertFalse(archive.remove(REF));
    }

    @Test
    public void storeAfterRemove_reindexesTheKnownVersion_withoutANewVersion() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.remove(REF);

        archive.store(new ResourceSnapshot(REF, V1, 300L));

        ArchivedResource record = records.findByRef(REF);
        assertEquals(1, record.versions().size());
        assertEquals(300L, record.indexedVersion().indexedAtMillis());
    }

    // ── removeByUri (tombstone) ──

    @Test
    public void removeByUri_tombstonesWithoutHistoryLoss() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.store(new ResourceSnapshot(REF, V2, 200L));

        assertTrue(archive.removeByUri(URI));

        ArchivedResource record = records.findByRef(REF);
        assertEquals(new SourceRemoval(NOW), record.sourceRemoval());
        assertEquals(2, record.versions().size());
    }

    @Test
    public void removeByUri_doesNotWithdrawTheIndexedFact() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));

        archive.removeByUri(URI);

        ArchivedResource record = records.findByRef(REF);
        assertTrue(record.isRemovedAtSource());
        assertTrue(record.isIndexed());
        assertEquals(record.latestObservedVersion(), record.indexedVersion().version());
        assertNotNull(archive.find(REF));
        assertFalse(archive.hasChanged(REF, V1));
    }

    @Test
    public void removeByUri_affectsAllKindsOfTheSameUri() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.store(new ResourceSnapshot(SAME_URI_AS_DIRECTORY, V2, 100L));

        assertTrue(archive.removeByUri(URI));

        assertTrue(records.findByRef(REF).isRemovedAtSource());
        assertTrue(records.findByRef(SAME_URI_AS_DIRECTORY).isRemovedAtSource());
    }

    @Test
    public void removeByUri_leavesOtherUrisUntouched() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.store(new ResourceSnapshot(OTHER, V1, 100L));

        archive.removeByUri(URI);

        assertFalse(records.findByRef(OTHER).isRemovedAtSource());
    }

    @Test
    public void removeByUri_returnsFalse_whenNoRecordExists() {
        archive.store(new ResourceSnapshot(OTHER, V1, 100L));

        assertFalse(archive.removeByUri(URI));
        assertFalse(archive.removeByUri(null));
        assertNull(records.findByRef(REF));
    }

    @Test
    public void removeByUri_affectsRecordsWithoutAnIndexedFact() {
        records.save(ArchivedResource.firstObservation(REF, V1, 100L));

        assertTrue(archive.removeByUri(URI));
        assertTrue(records.findByRef(REF).isRemovedAtSource());
    }

    // ── findByUri ──

    @Test
    public void findByUri_findsTheIndexedSnapshot_regardlessOfKind() {
        archive.store(new ResourceSnapshot(SAME_URI_AS_DIRECTORY, V2, 100L));

        ResourceSnapshot found = archive.findByUri(URI);

        assertEquals(SAME_URI_AS_DIRECTORY, found.ref());
        assertEquals(V2, found.digest());
    }

    @Test
    public void findByUri_skipsRecordsWithoutAnIndexedFact() {
        archive.store(new ResourceSnapshot(REF, V1, 100L));
        archive.store(new ResourceSnapshot(SAME_URI_AS_DIRECTORY, V2, 100L));
        archive.remove(REF);

        assertEquals(SAME_URI_AS_DIRECTORY, archive.findByUri(URI).ref());
        archive.remove(SAME_URI_AS_DIRECTORY);
        assertNull(archive.findByUri(URI));
        assertNull(archive.findByUri(null));
    }

    // ── InMemoryResourceArchive composition ──

    @Test
    public void inMemoryArchive_exposesTheRecordsBehindItsSnapshots() {
        InMemoryResourceArchive inMemory = new InMemoryResourceArchive();
        inMemory.store(new ResourceSnapshot(REF, V1, 100L));
        inMemory.remove(REF);

        assertNull(inMemory.find(REF));
        ArchivedResource record = inMemory.records().findByRef(REF);
        assertEquals(1, record.versions().size());
        assertFalse(record.isIndexed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void store_rejectsNull() {
        archive.store(null);
    }
}
