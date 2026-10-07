package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Contract for every {@link ResourceArchiveRepository} implementation. A persistent adapter
 * (e.g. H2) is expected to satisfy the same contract.
 */
public abstract class ResourceArchiveRepositoryContractTest {

    private static final BookmarkUri URI = BookmarkUri.of(ResourceScheme.FILE, "///tmp/shared");
    private static final VirtualResourceRef AS_FILE = new VirtualResourceRef(URI, VirtualResourceKind.FILE);
    private static final VirtualResourceRef AS_DIRECTORY = new VirtualResourceRef(URI, VirtualResourceKind.DIRECTORY);
    private static final VirtualResourceRef OTHER = new VirtualResourceRef(
            BookmarkUri.of(ResourceScheme.FILE, "///tmp/other"), VirtualResourceKind.FILE);
    private static final ResourceDigest V1 = ContentHasher.digest("v1".getBytes());
    private static final ResourceDigest V2 = ContentHasher.digest("v2".getBytes());

    private ResourceArchiveRepository repository;

    protected abstract ResourceArchiveRepository createRepository();

    @Before
    public void setUp() {
        repository = createRepository();
    }

    @Test
    public void unknownRef_isNotFound() {
        assertNull(repository.findByRef(AS_FILE));
        assertNull(repository.findByRef(null));
        assertTrue(repository.findByUri(URI).isEmpty());
    }

    @Test
    public void savedRecord_roundTripsWithAllFacts() {
        ArchivedResource record = ArchivedResource.firstObservation(AS_FILE, V1, 100L).observe(V2, 200L);
        record = record.markIndexed(record.versions().get(0), 150L).markRemovedAtSource(300L);

        repository.save(record);

        assertEquals(record, repository.findByRef(AS_FILE));
    }

    @Test
    public void save_replacesTheRecordForTheSameRef() {
        ArchivedResource first = ArchivedResource.firstObservation(AS_FILE, V1, 100L);
        repository.save(first);
        ArchivedResource second = first.observe(V2, 200L);
        repository.save(second);

        assertEquals(second, repository.findByRef(AS_FILE));
        assertEquals(1, repository.findByUri(URI).size());
    }

    @Test
    public void findByUri_returnsAllKinds_inFirstSaveOrder() {
        ArchivedResource file = ArchivedResource.firstObservation(AS_FILE, V1, 100L);
        ArchivedResource directory = ArchivedResource.firstObservation(AS_DIRECTORY, V2, 100L);
        repository.save(file);
        repository.save(ArchivedResource.firstObservation(OTHER, V1, 100L));
        repository.save(directory);
        repository.save(file.observe(V2, 200L));

        List<ArchivedResource> found = repository.findByUri(URI);

        assertEquals(Arrays.asList(AS_FILE, AS_DIRECTORY), Arrays.asList(found.get(0).ref(), found.get(1).ref()));
        assertEquals(2, found.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void save_rejectsNull() {
        repository.save(null);
    }
}
