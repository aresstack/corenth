package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link ExtractionRegistry}.
 */
public class ExtractionRegistryTest {

    @Test
    public void findsRegisteredExtractor() {
        ExtractionRegistry registry = new ExtractionRegistry();
        PlainTextExtractor textExtractor = new PlainTextExtractor();
        registry.register(textExtractor);

        DetectedContentType plainText = new DetectedContentType("text/plain", ContentCategory.PLAIN_TEXT, null);
        ResourceExtractor found = registry.findExtractor(plainText);
        assertSame(textExtractor, found);
    }

    @Test
    public void returnsNullWhenNoExtractorMatches() {
        ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new PlainTextExtractor());

        DetectedContentType pdf = new DetectedContentType("application/pdf", ContentCategory.PDF, null);
        assertNull(registry.findExtractor(pdf));
    }

    @Test
    public void firstMatchingExtractorWins() {
        ExtractionRegistry registry = new ExtractionRegistry();
        PlainTextExtractor first = new PlainTextExtractor();
        PlainTextExtractor second = new PlainTextExtractor();
        registry.register(first);
        registry.register(second);

        DetectedContentType plainText = new DetectedContentType("text/plain", ContentCategory.PLAIN_TEXT, null);
        assertSame(first, registry.findExtractor(plainText));
    }

    @Test
    public void multipleExtractorTypes() {
        ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new PlainTextExtractor());
        registry.register(new MarkdownTextExtractor());

        DetectedContentType markdown = new DetectedContentType("text/markdown", ContentCategory.MARKDOWN, null);
        ResourceExtractor found = registry.findExtractor(markdown);
        assertTrue(found instanceof MarkdownTextExtractor);
    }

    @Test
    public void sizeReflectsRegistrations() {
        ExtractionRegistry registry = new ExtractionRegistry();
        assertEquals(0, registry.size());
        registry.register(new PlainTextExtractor());
        assertEquals(1, registry.size());
        registry.register(new MarkdownTextExtractor());
        assertEquals(2, registry.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullExtractor() {
        new ExtractionRegistry().register(null);
    }
}
