package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.InspectionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeigmaContentInspectorTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final VirtualResourceRef REF =
            new VirtualResourceRef(BookmarkUri.parse("file:///docs/guide.md"), VirtualResourceKind.FILE);

    @Test
    public void detectsAndExtractsMarkdown() {
        InspectionResult result = standardInspector().inspect(REF, bytes("# Title\n\nBody text."), "guide.md");

        assertTrue(result.errorMessage(), result.isSuccess());
        assertEquals("text/markdown", result.mimeType());
        assertTrue(result.textBlocks().toString(), result.textBlocks().toString().contains("Body text."));
    }

    @Test
    public void failsWhenNoExtractorSupportsTheDetectedType() {
        InspectionResult result = standardInspector().inspect(REF, bytes("%PDF-1.7"), "report.pdf");

        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().contains("application/pdf"));
    }

    @Test
    public void passesOnlyTextBearingBlocks() {
        ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new FixedExtractor(false));
        DeigmaContentInspector inspector = new DeigmaContentInspector(new SimpleContentDetector(), registry);

        InspectionResult result = inspector.inspect(REF, bytes("ignored"), "notes.txt");

        assertTrue(result.isSuccess());
        assertEquals(Arrays.asList("heading", "paragraph"), result.textBlocks());
    }

    @Test
    public void translatesExtractorFailuresIntoInspectionFailures() {
        ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new FixedExtractor(true));
        DeigmaContentInspector inspector = new DeigmaContentInspector(new SimpleContentDetector(), registry);

        InspectionResult result = inspector.inspect(REF, bytes("ignored"), "notes.txt");

        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().contains("extractor exploded"));
    }

    private static DeigmaContentInspector standardInspector() {
        ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new PlainTextExtractor());
        registry.register(new MarkdownTextExtractor());
        return new DeigmaContentInspector(new SimpleContentDetector(), registry);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    private static final class FixedExtractor implements ResourceExtractor {
        private final boolean explode;

        FixedExtractor(boolean explode) {
            this.explode = explode;
        }

        @Override
        public boolean supports(DetectedContentType contentType) {
            return contentType.category() == ContentCategory.PLAIN_TEXT;
        }

        @Override
        public ExtractionResult extract(ExtractionRequest request) {
            if (explode) {
                throw new IllegalStateException("extractor exploded");
            }
            ExtractedDocument document = ExtractedDocument.builder()
                    .contentType(request.detectedContentType())
                    .addBlock(new ExtractedBlock(0, BlockKind.METADATA, "author=someone"))
                    .addBlock(new ExtractedBlock(1, BlockKind.HEADING, "heading"))
                    .addBlock(new ExtractedBlock(2, BlockKind.TEXT, "paragraph"))
                    .build();
            return ExtractionResult.success(request.resourceRef(), request.detectedContentType(), document);
        }
    }
}
