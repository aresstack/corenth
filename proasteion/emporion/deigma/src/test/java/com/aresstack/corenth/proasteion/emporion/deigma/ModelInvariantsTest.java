package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for immutability and validation invariants of public model types.
 */
public class ModelInvariantsTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static VirtualResourceRef testRef() {
        return new VirtualResourceRef(
                BookmarkUri.parse("file:///test/file.txt"),
                VirtualResourceKind.FILE);
    }

    private static DetectedContentType testType() {
        return new DetectedContentType("text/plain", ContentCategory.PLAIN_TEXT, null);
    }

    private static ExtractedDocument testDoc() {
        return ExtractedDocument.builder()
                .addBlock(new ExtractedBlock(0, BlockKind.TEXT, "content"))
                .build();
    }

    // ── ExtractionRequest immutability ───────────────────────────────────────

    @Test
    public void requestContentIsDefensivelyCopiedOnConstruction() {
        byte[] original = "hello".getBytes(UTF_8);
        ExtractionRequest req = new ExtractionRequest(testRef(), original, null, null);

        // Mutate original array
        original[0] = 'X';

        // Request should still have the original content
        assertEquals("hello", new String(req.content(), UTF_8));
    }

    @Test
    public void requestContentIsDefensivelyCopiedOnAccess() {
        ExtractionRequest req = new ExtractionRequest(testRef(), "hello".getBytes(UTF_8), null, null);

        byte[] returned = req.content();
        returned[0] = 'X';

        // Request content should be unchanged
        assertEquals("hello", new String(req.content(), UTF_8));
    }

    @Test
    public void requestCarriesDetectedContentType() {
        DetectedContentType type = testType();
        ExtractionRequest req = new ExtractionRequest(testRef(), "x".getBytes(UTF_8), null, null, type);
        assertSame(type, req.detectedContentType());
    }

    @Test
    public void requestDetectedContentTypeIsNullByDefault() {
        ExtractionRequest req = new ExtractionRequest(testRef(), "x".getBytes(UTF_8), null, null);
        assertNull(req.detectedContentType());
    }

    // ── ExtractionResult invariants ──────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void successRejectsNullResourceRef() {
        ExtractionResult.success(null, testType(), testDoc());
    }

    @Test(expected = IllegalArgumentException.class)
    public void successRejectsNullDetectedType() {
        ExtractionResult.success(testRef(), null, testDoc());
    }

    @Test(expected = IllegalArgumentException.class)
    public void successRejectsNullDocument() {
        ExtractionResult.success(testRef(), testType(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void failureRejectsNullResourceRef() {
        ExtractionResult.failure(null, testType(), "error");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failureRejectsNullErrorMessage() {
        ExtractionResult.failure(testRef(), testType(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void failureRejectsBlankErrorMessage() {
        ExtractionResult.failure(testRef(), testType(), "   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void successWithWarningsRejectsNullWarnings() {
        ExtractionResult.successWithWarnings(testRef(), testType(), testDoc(), null);
    }

    @Test
    public void successWithWarningsDefensivelyCopiesWarnings() {
        java.util.List<String> warnings = new java.util.ArrayList<String>();
        warnings.add("warn1");
        ExtractionResult result = ExtractionResult.successWithWarnings(testRef(), testType(), testDoc(), warnings);

        warnings.add("warn2");
        assertEquals(1, result.warnings().size());
    }

    @Test
    public void failureAllowsNullDetectedType() {
        ExtractionResult result = ExtractionResult.failure(testRef(), null, "detection failed");
        assertFalse(result.isSuccess());
        assertNull(result.detectedType());
    }

    // ── ExtractedBlock invariants ────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void blockRejectsNegativeIndex() {
        new ExtractedBlock(-1, BlockKind.TEXT, "text");
    }

    @Test(expected = IllegalArgumentException.class)
    public void blockRejectsNullKind() {
        new ExtractedBlock(0, null, "text");
    }

    @Test
    public void blockAllowsNullText() {
        ExtractedBlock block = new ExtractedBlock(0, BlockKind.METADATA, null);
        assertNull(block.text());
    }

    // ── ExtractedDocument.Builder invariants ─────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void documentBuilderRejectsNullBlock() {
        ExtractedDocument.builder().addBlock(null);
    }
}
