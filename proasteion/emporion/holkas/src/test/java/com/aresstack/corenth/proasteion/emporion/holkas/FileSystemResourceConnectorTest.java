package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileSystemResourceConnectorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void fetch_readsFileBytesAndMetadata() throws Exception {
        File file = temporaryFolder.newFile("note.txt");
        Files.write(file.toPath(), "hello".getBytes(StandardCharsets.UTF_8));

        FileSystemResourceConnector connector = new FileSystemResourceConnector();
        RawResource resource = connector.fetch(ref(file, VirtualResourceKind.FILE));

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), resource.content().bytes());
        assertEquals("note.txt", resource.metadata().name());
        assertEquals(5L, resource.metadata().sizeBytes());
        assertEquals(VirtualResourceKind.FILE, resource.metadata().kind());
        assertTrue(resource.metadata().modifiedAtMillis() > 0L);
    }

    @Test
    public void list_returnsDirectoryChildren() throws Exception {
        File dir = temporaryFolder.newFolder("root");
        File childFile = new File(dir, "a.txt");
        File childDir = new File(dir, "sub");
        Files.write(childFile.toPath(), "alpha".getBytes(StandardCharsets.UTF_8));
        assertTrue(childDir.mkdir());

        FileSystemResourceConnector connector = new FileSystemResourceConnector();
        ResourceListing listing = connector.list(ref(dir, VirtualResourceKind.DIRECTORY));

        List<ResourceListingEntry> entries = listing.entries();
        assertEquals(2, entries.size());
        assertEquals("a.txt", entries.get(0).name());
        assertEquals(VirtualResourceKind.FILE, entries.get(0).kind());
        assertEquals("sub", entries.get(1).name());
        assertEquals(VirtualResourceKind.DIRECTORY, entries.get(1).kind());
    }

    @Test
    public void fetch_rejectsDirectory() throws Exception {
        File dir = temporaryFolder.newFolder("root");
        FileSystemResourceConnector connector = new FileSystemResourceConnector();

        try {
            connector.fetch(ref(dir, VirtualResourceKind.DIRECTORY));
        } catch (ResourceConnectorException e) {
            assertTrue(e.getMessage().contains("regular file"));
            return;
        }
        throw new AssertionError("Expected ResourceConnectorException");
    }

    @Test
    public void list_rejectsFile() throws Exception {
        File file = temporaryFolder.newFile("note.txt");
        FileSystemResourceConnector connector = new FileSystemResourceConnector();

        try {
            connector.list(ref(file, VirtualResourceKind.FILE));
        } catch (ResourceConnectorException e) {
            assertTrue(e.getMessage().contains("directory"));
            return;
        }
        throw new AssertionError("Expected ResourceConnectorException");
    }

    @Test
    public void supports_fileSchemeOnly() {
        FileSystemResourceConnector connector = new FileSystemResourceConnector();

        assertTrue(connector.supports(ResourceScheme.FILE));
    }

    private VirtualResourceRef ref(File file, VirtualResourceKind kind) {
        return new VirtualResourceRef(BookmarkUri.parse(file.toURI().toString()), kind);
    }
}
