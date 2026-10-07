package com.aresstack.corenth.proasteion.emporion.deigma.html;

import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
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

import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.UTF_8;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.fixture;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.ref;
import static org.junit.Assert.*;

/**
 * Detection → registry selection → extraction through the existing Deigma contracts only.
 */
public class HtmlExtractionRegistryTest {

    private final ContentDetector detector = new SimpleContentDetector();
    private final ExtractionRegistry registry = new ExtractionRegistry();

    public HtmlExtractionRegistryTest() {
        registry.register(new PlainTextExtractor());
        registry.register(new MarkdownTextExtractor());
        registry.register(new HtmlDocumentExtractor());
    }

    private ExtractionResult detectAndExtract(String filename, String contentTypeHint, byte[] content) {
        DetectedContentType type = detector.detect(filename, contentTypeHint, Arrays.copyOf(content, Math.min(content.length, 512)));
        ResourceExtractor extractor = registry.findExtractor(type);
        assertNotNull("no extractor for " + type, extractor);
        return extractor.extract(new ExtractionRequest(ref(), content, filename, contentTypeHint, type));
    }

    @Test
    public void selectsHtmlExtractorByExtension() {
        DetectedContentType type = detector.detect("bericht.html", null, null);
        assertTrue(registry.findExtractor(type) instanceof HtmlDocumentExtractor);
    }

    @Test
    public void selectsHtmlExtractorByMimeHint() {
        DetectedContentType type = detector.detect(null, "text/html; charset=utf-8", null);
        assertTrue(registry.findExtractor(type) instanceof HtmlDocumentExtractor);
    }

    @Test
    public void selectsHtmlExtractorForXhtml() {
        DetectedContentType type = detector.detect("seite.xhtml", null, null);
        assertTrue(registry.findExtractor(type) instanceof HtmlDocumentExtractor);
    }

    @Test
    public void selectsHtmlExtractorForSniffedContentWithoutHints() {
        ExtractionResult result = detectAndExtract(null, null, fixture("article.html"));
        assertTrue(result.isSuccess());
        assertEquals(ContentCategory.HTML, result.detectedType().category());
        assertEquals("Hafenbericht Korinth", result.document().title());
    }

    @Test
    public void plainTextAndMarkdownKeepTheirExtractors() {
        assertTrue(registry.findExtractor(detector.detect("notiz.txt", null, null)) instanceof PlainTextExtractor);
        assertTrue(registry.findExtractor(detector.detect("README.md", null, null)) instanceof MarkdownTextExtractor);
    }

    @Test
    public void unsupportedCategoryFindsNoExtractor() {
        assertNull(registry.findExtractor(detector.detect("scan.pdf", null, null)));
    }

    @Test
    public void endToEndExtractionCarriesTheDetectedType() {
        ExtractionResult result = detectAndExtract("bericht.htm", null, "<h1>Titel</h1><p>Text</p>".getBytes(UTF_8));
        assertTrue(result.isSuccess());
        assertEquals("text/html", result.detectedType().mimeType());
        assertEquals("Titel\nText", result.document().combinedText());
    }
}
