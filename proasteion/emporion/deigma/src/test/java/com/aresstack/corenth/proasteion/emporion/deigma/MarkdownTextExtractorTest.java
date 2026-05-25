package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link MarkdownTextExtractor}.
 */
public class MarkdownTextExtractorTest {

    private final MarkdownTextExtractor extractor = new MarkdownTextExtractor();

    private ExtractionRequest request(String content) {
        VirtualResourceRef ref = new VirtualResourceRef(
                BookmarkUri.parse("file:///test/doc.md"),
                VirtualResourceKind.FILE);
        return new ExtractionRequest(ref, content.getBytes(), "doc.md", null);
    }

    @Test
    public void supportsMarkdown() {
        DetectedContentType md = new DetectedContentType("text/markdown", ContentCategory.MARKDOWN, null);
        assertTrue(extractor.supports(md));
    }

    @Test
    public void doesNotSupportPlainText() {
        DetectedContentType plain = new DetectedContentType("text/plain", ContentCategory.PLAIN_TEXT, null);
        assertFalse(extractor.supports(plain));
    }

    @Test
    public void extractsHeading() {
        ExtractionResult result = extractor.extract(request("# Title"));

        assertTrue(result.isSuccess());
        assertEquals(1, result.document().blocks().size());
        ExtractedBlock block = result.document().blocks().get(0);
        assertEquals(BlockKind.HEADING, block.kind());
        assertEquals("Title", block.text());
        assertEquals("1", block.attributes().get("level"));
    }

    @Test
    public void extractsMultiLevelHeadings() {
        String content = "# H1\n## H2\n### H3";
        ExtractionResult result = extractor.extract(request(content));

        assertEquals(3, result.document().blocks().size());
        assertEquals("1", result.document().blocks().get(0).attributes().get("level"));
        assertEquals("2", result.document().blocks().get(1).attributes().get("level"));
        assertEquals("3", result.document().blocks().get(2).attributes().get("level"));
    }

    @Test
    public void extractsCodeBlock() {
        String content = "```java\npublic class Foo {}\n```";
        ExtractionResult result = extractor.extract(request(content));

        assertEquals(1, result.document().blocks().size());
        ExtractedBlock block = result.document().blocks().get(0);
        assertEquals(BlockKind.CODE, block.kind());
        assertEquals("public class Foo {}", block.text());
        assertEquals("java", block.attributes().get("language"));
    }

    @Test
    public void extractsCodeBlockWithoutLanguage() {
        String content = "```\nsome code\n```";
        ExtractionResult result = extractor.extract(request(content));

        assertEquals(1, result.document().blocks().size());
        ExtractedBlock block = result.document().blocks().get(0);
        assertEquals(BlockKind.CODE, block.kind());
        assertEquals("some code", block.text());
    }

    @Test
    public void extractsMixedContent() {
        String content = "# Introduction\n\nSome text here.\n\n```\ncode\n```\n\nMore text.";
        ExtractionResult result = extractor.extract(request(content));

        assertTrue(result.isSuccess());
        // Heading, text, code, text
        assertTrue(result.document().blocks().size() >= 4);
        assertEquals(BlockKind.HEADING, result.document().blocks().get(0).kind());
        assertEquals("Introduction", result.document().blocks().get(0).text());
    }

    @Test
    public void plainTextLinesBecomeTEXTBlocks() {
        String content = "Just a paragraph\nwith two lines.";
        ExtractionResult result = extractor.extract(request(content));

        assertEquals(1, result.document().blocks().size());
        assertEquals(BlockKind.TEXT, result.document().blocks().get(0).kind());
        assertTrue(result.document().blocks().get(0).text().contains("Just a paragraph"));
    }

    @Test
    public void combinedTextIncludesAll() {
        String content = "# Title\n\nBody text.";
        ExtractionResult result = extractor.extract(request(content));

        String combined = result.document().combinedText();
        assertTrue(combined.contains("Title"));
        assertTrue(combined.contains("Body text."));
    }
}
