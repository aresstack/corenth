package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link SimpleContentDetector}.
 */
public class SimpleContentDetectorTest {

    private final ContentDetector detector = new SimpleContentDetector();

    // ── Plain text detection ─────────────────────────────────────────────────

    @Test
    public void detectsPlainTextByExtension() {
        DetectedContentType result = detector.detect("readme.txt", null, null);
        assertEquals("text/plain", result.mimeType());
        assertEquals(ContentCategory.PLAIN_TEXT, result.category());
    }

    @Test
    public void detectsLogFileAsPlainText() {
        DetectedContentType result = detector.detect("server.log", null, null);
        assertEquals("text/plain", result.mimeType());
        assertEquals(ContentCategory.PLAIN_TEXT, result.category());
    }

    // ── Markdown detection ───────────────────────────────────────────────────

    @Test
    public void detectsMarkdownByExtension() {
        DetectedContentType result = detector.detect("README.md", null, null);
        assertEquals("text/markdown", result.mimeType());
        assertEquals(ContentCategory.MARKDOWN, result.category());
    }

    @Test
    public void detectsMarkdownLongExtension() {
        DetectedContentType result = detector.detect("notes.markdown", null, null);
        assertEquals("text/markdown", result.mimeType());
        assertEquals(ContentCategory.MARKDOWN, result.category());
    }

    // ── HTML detection ───────────────────────────────────────────────────────

    @Test
    public void detectsHtmlByExtension() {
        DetectedContentType result = detector.detect("page.html", null, null);
        assertEquals("text/html", result.mimeType());
        assertEquals(ContentCategory.HTML, result.category());
    }

    @Test
    public void detectsHtmByExtension() {
        DetectedContentType result = detector.detect("page.htm", null, null);
        assertEquals("text/html", result.mimeType());
        assertEquals(ContentCategory.HTML, result.category());
    }

    // ── PDF detection ────────────────────────────────────────────────────────

    @Test
    public void detectsPdfByExtension() {
        DetectedContentType result = detector.detect("document.pdf", null, null);
        assertEquals("application/pdf", result.mimeType());
        assertEquals(ContentCategory.PDF, result.category());
    }

    @Test
    public void detectsPdfByMagicBytes() {
        byte[] pdfMagic = "%PDF-1.4".getBytes();
        DetectedContentType result = detector.detect(null, null, pdfMagic);
        assertEquals("application/pdf", result.mimeType());
        assertEquals(ContentCategory.PDF, result.category());
    }

    // ── Source code detection ────────────────────────────────────────────────

    @Test
    public void detectsJavaAsSourceCode() {
        DetectedContentType result = detector.detect("Main.java", null, null);
        assertEquals(ContentCategory.SOURCE_CODE, result.category());
    }

    @Test
    public void detectsCobolAsSourceCode() {
        DetectedContentType result = detector.detect("PROGRAM.cbl", null, null);
        assertEquals(ContentCategory.SOURCE_CODE, result.category());
    }

    @Test
    public void detectsJclAsSourceCode() {
        DetectedContentType result = detector.detect("JOB1.jcl", null, null);
        assertEquals(ContentCategory.SOURCE_CODE, result.category());
    }

    @Test
    public void detectsPythonAsSourceCode() {
        DetectedContentType result = detector.detect("script.py", null, null);
        assertEquals(ContentCategory.SOURCE_CODE, result.category());
    }

    @Test
    public void detectsNaturalAsSourceCode() {
        DetectedContentType result = detector.detect("MODULE.nat", null, null);
        assertEquals(ContentCategory.SOURCE_CODE, result.category());
    }

    // ── MIME hint detection ──────────────────────────────────────────────────

    @Test
    public void detectsByMimeHint() {
        DetectedContentType result = detector.detect(null, "application/json", null);
        assertEquals("application/json", result.mimeType());
        assertEquals(ContentCategory.STRUCTURED_DATA, result.category());
    }

    @Test
    public void detectsByMimeHintWithCharset() {
        DetectedContentType result = detector.detect(null, "text/html; charset=utf-8", null);
        assertEquals("text/html", result.mimeType());
        assertEquals(ContentCategory.HTML, result.category());
    }

    @Test
    public void unknownTextMimeFallsToPlainText() {
        DetectedContentType result = detector.detect(null, "text/x-custom", null);
        assertEquals("text/x-custom", result.mimeType());
        assertEquals(ContentCategory.PLAIN_TEXT, result.category());
    }

    // ── Office documents ─────────────────────────────────────────────────────

    @Test
    public void detectsDocx() {
        DetectedContentType result = detector.detect("report.docx", null, null);
        assertEquals(ContentCategory.OFFICE_DOCUMENT, result.category());
    }

    @Test
    public void detectsXlsx() {
        DetectedContentType result = detector.detect("data.xlsx", null, null);
        assertEquals(ContentCategory.OFFICE_DOCUMENT, result.category());
    }

    // ── Structured data ──────────────────────────────────────────────────────

    @Test
    public void detectsJson() {
        DetectedContentType result = detector.detect("config.json", null, null);
        assertEquals(ContentCategory.STRUCTURED_DATA, result.category());
    }

    @Test
    public void detectsCsv() {
        DetectedContentType result = detector.detect("export.csv", null, null);
        assertEquals(ContentCategory.STRUCTURED_DATA, result.category());
    }

    @Test
    public void detectsYaml() {
        DetectedContentType result = detector.detect("config.yml", null, null);
        assertEquals(ContentCategory.STRUCTURED_DATA, result.category());
    }

    // ── Fallback ─────────────────────────────────────────────────────────────

    @Test
    public void unknownExtensionReturnsUnknown() {
        DetectedContentType result = detector.detect("file.zzz", null, null);
        assertEquals(ContentCategory.UNKNOWN, result.category());
    }

    @Test
    public void noHintsReturnsUnknown() {
        DetectedContentType result = detector.detect(null, null, null);
        assertEquals(ContentCategory.UNKNOWN, result.category());
    }

    // ── Filename with path ───────────────────────────────────────────────────

    @Test
    public void handlesFilenameWithPath() {
        DetectedContentType result = detector.detect("/some/path/to/file.md", null, null);
        assertEquals(ContentCategory.MARKDOWN, result.category());
    }

    @Test
    public void handlesWindowsPathSeparator() {
        DetectedContentType result = detector.detect("C:\\docs\\readme.txt", null, null);
        assertEquals(ContentCategory.PLAIN_TEXT, result.category());
    }
}
