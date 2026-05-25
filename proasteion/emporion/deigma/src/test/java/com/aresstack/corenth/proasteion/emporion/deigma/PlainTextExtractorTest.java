package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link PlainTextExtractor}.
 */
public class PlainTextExtractorTest {

    private final PlainTextExtractor extractor = new PlainTextExtractor();

    private ExtractionRequest request(String content, String filename) {
        VirtualResourceRef ref = new VirtualResourceRef(
                BookmarkUri.parse("file:///test/" + (filename != null ? filename : "test.txt")),
                VirtualResourceKind.FILE);
        return new ExtractionRequest(ref, content.getBytes(), filename, null);
    }

    @Test
    public void supportsPlainText() {
        DetectedContentType plainText = new DetectedContentType("text/plain", ContentCategory.PLAIN_TEXT, null);
        assertTrue(extractor.supports(plainText));
    }

    @Test
    public void doesNotSupportMarkdown() {
        DetectedContentType markdown = new DetectedContentType("text/markdown", ContentCategory.MARKDOWN, null);
        assertFalse(extractor.supports(markdown));
    }

    @Test
    public void extractsSimpleText() {
        ExtractionResult result = extractor.extract(request("Hello, world!", "hello.txt"));

        assertTrue(result.isSuccess());
        assertNotNull(result.document());
        assertEquals(1, result.document().blocks().size());

        ExtractedBlock block = result.document().blocks().get(0);
        assertEquals(BlockKind.TEXT, block.kind());
        assertEquals("Hello, world!", block.text());
        assertEquals(0, block.index());
    }

    @Test
    public void extractsMultilineText() {
        String content = "Line 1\nLine 2\nLine 3";
        ExtractionResult result = extractor.extract(request(content, "multi.txt"));

        assertTrue(result.isSuccess());
        assertEquals(content, result.document().blocks().get(0).text());
    }

    @Test
    public void combinedTextMatchesFullContent() {
        String content = "All content in one block";
        ExtractionResult result = extractor.extract(request(content, "test.txt"));

        assertEquals(content, result.document().combinedText());
    }

    @Test
    public void emptyContentProducesEmptyBlock() {
        ExtractionResult result = extractor.extract(request("", "empty.txt"));

        assertTrue(result.isSuccess());
        assertEquals(1, result.document().blocks().size());
        assertEquals("", result.document().blocks().get(0).text());
    }

    @Test
    public void resultCarriesResourceRef() {
        VirtualResourceRef ref = new VirtualResourceRef(
                BookmarkUri.parse("file:///docs/readme.txt"),
                VirtualResourceKind.FILE);
        ExtractionRequest req = new ExtractionRequest(ref, "content".getBytes(), "readme.txt", null);

        ExtractionResult result = extractor.extract(req);
        assertEquals(ref, result.resourceRef());
    }
}
