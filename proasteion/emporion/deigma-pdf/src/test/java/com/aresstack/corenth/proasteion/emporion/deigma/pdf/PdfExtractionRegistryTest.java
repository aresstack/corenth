package com.aresstack.corenth.proasteion.emporion.deigma.pdf;

import com.aresstack.corenth.proasteion.emporion.deigma.ContentDetector;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import org.junit.Test;

import java.util.Arrays;

import static com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfFixtures.pages;
import static com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfFixtures.ref;
import static org.junit.Assert.*;

/**
 * Detection → registry selection → extraction through the existing Deigma contracts only.
 */
public class PdfExtractionRegistryTest {

    private final ContentDetector detector = new SimpleContentDetector();
    private final ExtractionRegistry registry = new ExtractionRegistry();

    public PdfExtractionRegistryTest() {
        registry.register(new PlainTextExtractor());
        registry.register(new MarkdownTextExtractor());
        registry.register(new PdfDocumentExtractor());
    }

    @Test
    public void selectsPdfExtractorByExtensionAndMimeHint() {
        assertTrue(registry.findExtractor(detector.detect("bericht.pdf", null, null)) instanceof PdfDocumentExtractor);
        assertTrue(registry.findExtractor(detector.detect(null, "application/pdf", null)) instanceof PdfDocumentExtractor);
    }

    @Test
    public void magicBytesSelectPdfExtractorDespiteMisleadingName() {
        byte[] pdf = pages("Inhalt");
        DetectedContentType type = detector.detect("notiz.txt", null, Arrays.copyOf(pdf, 8));
        ResourceExtractor extractor = registry.findExtractor(type);
        assertTrue(extractor instanceof PdfDocumentExtractor);
        ExtractionResult result = extractor.extract(new ExtractionRequest(ref(), pdf, "notiz.txt", null, type));
        assertTrue(result.isSuccess());
        assertEquals("Inhalt", result.document().combinedText());
    }

    @Test
    public void plainTextAndMarkdownKeepTheirExtractors() {
        assertTrue(registry.findExtractor(detector.detect("notiz.txt", null, null)) instanceof PlainTextExtractor);
        assertTrue(registry.findExtractor(detector.detect("README.md", null, null)) instanceof MarkdownTextExtractor);
    }
}
