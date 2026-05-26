package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceFingerprint;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for chalcotheca archive/cache/lifecycle model classes.
 */
public class ChalcothecaModelTest {

    private static final VirtualResourceRef REF =
            new VirtualResourceRef(
                    BookmarkUri.of(ResourceScheme.FILE, "///tmp/readme.md"),
                    VirtualResourceKind.FILE);

    // ── ContentHasher ──

    @Test
    public void contentHasher_producesConsistentResult() {
        String h1 = ContentHasher.hash("Hello, World!");
        String h2 = ContentHasher.hash("Hello, World!");
        assertEquals(h1, h2);
        assertEquals(64, h1.length()); // SHA-256 hex = 64 chars
    }

    @Test
    public void contentHasher_differsByContent() {
        assertNotEquals(ContentHasher.hash("A"), ContentHasher.hash("B"));
    }

    @Test
    public void contentHasher_fingerprintFromBytes() {
        ResourceFingerprint fp = ContentHasher.fingerprint(new byte[]{1, 2, 3});
        assertEquals("SHA-256", fp.algorithm());
        assertEquals(64, fp.hash().length());
    }

    @Test
    public void contentHasher_digestIncludesSize() {
        byte[] data = "test content".getBytes();
        ResourceDigest d = ContentHasher.digest(data);
        assertEquals(data.length, d.sizeBytes());
        assertEquals("SHA-256", d.fingerprint().algorithm());
    }

    @Test(expected = IllegalArgumentException.class)
    public void contentHasher_nullBytesThrows() {
        ContentHasher.hash((byte[]) null);
    }

    // ── ResourceLifecycleState ──

    @Test
    public void lifecycleState_valuesExist() {
        assertNotNull(ResourceLifecycleState.PENDING);
        assertNotNull(ResourceLifecycleState.ACQUIRED);
        assertNotNull(ResourceLifecycleState.CACHED);
        assertNotNull(ResourceLifecycleState.INDEXED);
        assertNotNull(ResourceLifecycleState.STALE);
        assertNotNull(ResourceLifecycleState.TOMBSTONED);
    }

    // ── ResourceVersion ──

    @Test
    public void resourceVersion_storesDigestAndTimestamp() {
        ResourceDigest digest = ContentHasher.digest("v1".getBytes());
        ResourceVersion v = new ResourceVersion(digest, 1000L);
        assertEquals(digest, v.digest());
        assertEquals(1000L, v.observedAtMillis());
    }

    @Test
    public void resourceVersion_equality() {
        ResourceDigest d = ContentHasher.digest("x".getBytes());
        ResourceVersion a = new ResourceVersion(d, 500L);
        ResourceVersion b = new ResourceVersion(d, 500L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void resourceVersion_nullDigestThrows() {
        new ResourceVersion(null, 0);
    }

    // ── ArchivedResource ──

    @Test
    public void archivedResource_defaultState() {
        ArchivedResource ar = new ArchivedResource(REF);
        assertEquals(REF, ar.ref());
        assertEquals(ResourceLifecycleState.PENDING, ar.state());
        assertNull(ar.currentVersion());
    }

    @Test
    public void archivedResource_tombstone() {
        ArchivedResource ar = new ArchivedResource(REF);
        ar.tombstone(9999L);
        assertEquals(ResourceLifecycleState.TOMBSTONED, ar.state());
        assertEquals(9999L, ar.tombstonedAtMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void archivedResource_nullRefThrows() {
        new ArchivedResource(null);
    }

    // ── ResourceArchiveRepository ──

    @Test
    public void repository_saveAndFind() {
        InMemoryResourceArchiveRepository repo = new InMemoryResourceArchiveRepository();
        ArchivedResource ar = new ArchivedResource(REF);
        repo.save(ar);
        assertSame(ar, repo.findByRef(REF));
    }

    @Test
    public void repository_findByState() {
        InMemoryResourceArchiveRepository repo = new InMemoryResourceArchiveRepository();
        ArchivedResource ar = new ArchivedResource(REF);
        ar.setState(ResourceLifecycleState.INDEXED);
        repo.save(ar);

        List<ArchivedResource> indexed = repo.findByState(ResourceLifecycleState.INDEXED);
        assertEquals(1, indexed.size());
        assertTrue(repo.findByState(ResourceLifecycleState.PENDING).isEmpty());
    }

    @Test
    public void repository_remove() {
        InMemoryResourceArchiveRepository repo = new InMemoryResourceArchiveRepository();
        repo.save(new ArchivedResource(REF));
        assertTrue(repo.remove(REF));
        assertNull(repo.findByRef(REF));
        assertFalse(repo.remove(REF));
    }

    // ── ResourceArchive.remove ──

    @Test
    public void archive_remove() {
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        ResourceDigest digest = ContentHasher.digest("hello".getBytes());
        archive.store(new ResourceSnapshot(REF, digest, System.currentTimeMillis()));
        assertTrue(archive.remove(REF));
        assertNull(archive.find(REF));
        assertFalse(archive.remove(REF));
    }
}
