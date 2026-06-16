package com.aresstack.corenth.proasteion.emporion;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import com.aresstack.corenth.proasteion.emporion.holkas.DefaultResourceConnectorRegistry;
import com.aresstack.corenth.proasteion.emporion.holkas.FileSystemResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceListing;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultResourceHarborTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void inspect_fetchesDetectsAndExtractsPlainText() throws Exception {
        File file = temporaryFolder.newFile("note.txt");
        Files.write(file.toPath(), "hello harbor".getBytes(StandardCharsets.UTF_8));
        ResourceHarbor harbor = fileHarbor();

        HarborResult<HarborInspection> result = harbor.inspect(new HarborRequest(ref(file, VirtualResourceKind.FILE)));

        assertTrue(result.isSuccess());
        assertTrue(result.value().extractionResult().isSuccess());
        assertEquals(ContentCategory.PLAIN_TEXT, result.value().detectedContentType().category());
        assertEquals("hello harbor", result.value().extractionResult().document().combinedText());
    }

    @Test
    public void inspect_returnsSuccessfulHarborResultWithExtractionFailureWhenNoExtractorMatches() throws Exception {
        File file = temporaryFolder.newFile("data.bin");
        Files.write(file.toPath(), new byte[] {0, 1, 2});
        ResourceHarbor harbor = fileHarbor();

        HarborResult<HarborInspection> result = harbor.inspect(new HarborRequest(ref(file, VirtualResourceKind.FILE)));

        assertTrue(result.isSuccess());
        assertFalse(result.value().extractionResult().isSuccess());
        assertEquals(ContentCategory.UNKNOWN, result.value().detectedContentType().category());
        assertTrue(result.value().extractionResult().errorMessage().contains("No extractor"));
    }

    @Test
    public void list_delegatesToHolkasWithoutExtraction() throws Exception {
        File dir = temporaryFolder.newFolder("root");
        File child = new File(dir, "a.txt");
        Files.write(child.toPath(), "alpha".getBytes(StandardCharsets.UTF_8));
        ResourceHarbor harbor = fileHarbor();

        HarborResult<ResourceListing> result = harbor.list(new HarborRequest(ref(dir, VirtualResourceKind.DIRECTORY)));

        assertTrue(result.isSuccess());
        assertEquals(1, result.value().entries().size());
        assertEquals("a.txt", result.value().entries().get(0).name());
    }

    @Test
    public void list_reportsConnectorFailureAsHarborFailure() throws Exception {
        File file = temporaryFolder.newFile("note.txt");
        ResourceHarbor harbor = fileHarbor();

        HarborResult<ResourceListing> result = harbor.list(new HarborRequest(ref(file, VirtualResourceKind.FILE)));

        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().contains("directory"));
    }

    private ResourceHarbor fileHarbor() {
        ExtractionRegistry extractionRegistry = new ExtractionRegistry();
        extractionRegistry.register(new PlainTextExtractor());
        extractionRegistry.register(new MarkdownTextExtractor());
        return new DefaultResourceHarbor(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()),
                new SimpleContentDetector(),
                extractionRegistry);
    }

    private VirtualResourceRef ref(File file, VirtualResourceKind kind) {
        return new VirtualResourceRef(BookmarkUri.parse(file.toURI().toString()), kind);
    }
}
