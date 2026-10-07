package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.ResourceFingerprint;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for chalcotheca hashing utilities. Resource records and the archive facade are
 * covered by {@link ArchivedResourceTest} and {@link RecordBackedResourceArchiveTest}.
 */
public class ChalcothecaModelTest {

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
}
