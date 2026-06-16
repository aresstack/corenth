package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
}
