package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionCapability;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionLimitExceededException;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeMetadata;
import com.aresstack.corenth.astu.acropolis.chalcotheca.SourceAbsentException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HolkasAcquisitionPortTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void fetchContent_mapsRawResourceToBronzeContent() throws Exception {
        File file = temporaryFolder.newFile("note.txt");
        Files.write(file.toPath(), "hello".getBytes(StandardCharsets.UTF_8));
        HolkasAcquisitionPort port = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));

        BronzeContent content = port.fetchContent(BookmarkUri.parse(file.toURI().toString()));

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), content.content());
        assertEquals(5L, content.digest().sizeBytes());
        assertTrue(content.fetchedAtMillis() > 0L);
    }

    @Test
    public void listChildren_mapsResourceListingToBronzeListing() throws Exception {
        File dir = temporaryFolder.newFolder("root");
        File child = new File(dir, "a.txt");
        Files.write(child.toPath(), "alpha".getBytes(StandardCharsets.UTF_8));
        HolkasAcquisitionPort port = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));

        BronzeListing listing = port.listChildren(BookmarkUri.parse(dir.toURI().toString()));

        assertEquals(1, listing.entries().size());
        assertEquals("a.txt", listing.entries().get(0).name());
        assertEquals(VirtualResourceKind.FILE, listing.entries().get(0).kind());
    }

    @Test
    public void fetchContent_failsForUnknownScheme() throws Exception {
        HolkasAcquisitionPort port = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));

        try {
            port.fetchContent(BookmarkUri.parse("ftp://example.org/file.txt"));
        } catch (ResourceConnectorException e) {
            assertTrue(e.getMessage().contains(ResourceScheme.FTP.name()));
            return;
        }
        throw new AssertionError("Expected ResourceConnectorException");
    }

    @Test
    public void fetchMetadata_readsTheFileSize_withoutTheContent() throws Exception {
        File file = temporaryFolder.newFile("sized.txt");
        Files.write(file.toPath(), "twelve bytes".getBytes(StandardCharsets.UTF_8));
        HolkasAcquisitionPort port = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));
        BookmarkUri uri = BookmarkUri.parse(file.toURI().toString());

        assertTrue(port.offersMetadata(uri));
        BronzeMetadata metadata = port.fetchMetadata(uri, null);

        assertEquals(12L, metadata.sizeBytes());
        assertEquals("sized.txt", metadata.name());
    }

    @Test
    public void missingFile_isAConfirmedAbsence_forContentAndMetadata() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "missing.txt");
        HolkasAcquisitionPort port = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));
        BookmarkUri uri = BookmarkUri.parse(file.toURI().toString());

        try {
            port.fetchContent(uri);
            fail("a missing file must be reported as absent");
        } catch (SourceAbsentException expected) {
            // confirmed absence
        }
        try {
            port.fetchMetadata(uri, null);
            fail("a missing file must be reported as absent");
        } catch (SourceAbsentException expected) {
            // confirmed absence
        }
    }

    @Test
    public void directoryInsteadOfFile_isAnErrorButNoAbsence() throws Exception {
        File dir = temporaryFolder.newFolder("not-a-file");
        HolkasAcquisitionPort port = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));

        try {
            port.fetchMetadata(BookmarkUri.parse(dir.toURI().toString()), null);
            fail("a directory has no file metadata");
        } catch (SourceAbsentException unexpected) {
            fail("an existing directory is not absent");
        } catch (java.io.IOException expected) {
            // ordinary connector error
        }
    }

    @Test
    public void unregisteredScheme_offersNoMetadata() {
        HolkasAcquisitionPort port = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));
        assertFalse(port.offersMetadata(BookmarkUri.parse("ndv://host/LIB/MEMBER")));
    }

    @Test
    public void boundedFetch_returnsAFileAtTheLimit() throws Exception {
        File file = temporaryFolder.newFile("exact.txt");
        Files.write(file.toPath(), "0123456789".getBytes(StandardCharsets.UTF_8));

        BronzeContent content = filePort().fetchContent(BookmarkUri.parse(file.toURI().toString()), null, 10L);

        assertArrayEquals("0123456789".getBytes(StandardCharsets.UTF_8), content.content());
    }

    @Test
    public void boundedFetch_stopsOneByteBeyondTheLimit_forALargeFile() throws Exception {
        File file = temporaryFolder.newFile("large.bin");
        Files.write(file.toPath(), new byte[1024 * 1024]);

        try {
            filePort().fetchContent(BookmarkUri.parse(file.toURI().toString()), null, 10L);
            fail("an oversized file must not be returned");
        } catch (AcquisitionLimitExceededException e) {
            assertEquals("the read stopped after limit + 1 bytes", 11L, e.observedBytes());
        }
    }

    @Test
    public void boundedFetch_ofAMissingFile_isAConfirmedAbsence() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "gone.txt");
        try {
            filePort().fetchContent(BookmarkUri.parse(file.toURI().toString()), null, 10L);
            fail("expected a confirmed absence");
        } catch (SourceAbsentException expected) {
            // expected
        }
    }

    @Test
    public void boundedFetch_withACapability_isRefused_insteadOfReadUnbounded() throws Exception {
        File file = temporaryFolder.newFile("secured.txt");
        Files.write(file.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try {
            filePort().fetchContent(BookmarkUri.parse(file.toURI().toString()), new AcquisitionCapability() {
                @Override public String grantId() { return "grant"; }
                @Override public String targetSystem() { return "file"; }
                @Override public long expiresAtEpochMillis() { return Long.MAX_VALUE; }
                @Override public void close() { }
            }, 10L);
            fail("authenticated connectors cannot bound their read yet");
        } catch (ResourceConnectorException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("cannot bound"));
        }
    }

    private static HolkasAcquisitionPort filePort() {
        return new HolkasAcquisitionPort(DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));
    }
}
