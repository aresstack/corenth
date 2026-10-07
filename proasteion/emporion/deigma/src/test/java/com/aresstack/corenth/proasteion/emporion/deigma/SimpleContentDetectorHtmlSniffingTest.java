package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.*;

/**
 * Tests for HTML content sniffing in {@link SimpleContentDetector}.
 */
public class SimpleContentDetectorHtmlSniffingTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final ContentDetector detector = new SimpleContentDetector();

    private DetectedContentType sniff(String prefix) {
        return detector.detect(null, null, prefix.getBytes(UTF_8));
    }

    @Test
    public void detectsDoctypeWithoutHints() {
        DetectedContentType result = sniff("<!DOCTYPE html>\n<html><body>x</body></html>");
        assertEquals("text/html", result.mimeType());
        assertEquals(ContentCategory.HTML, result.category());
    }

    @Test
    public void detectsHtmlRootElementCaseInsensitively() {
        assertEquals(ContentCategory.HTML, sniff("<HTML lang=\"de\">").category());
        assertEquals(ContentCategory.HTML, sniff("<html>").category());
    }

    @Test
    public void detectsHeadOrBodyFragments() {
        assertEquals(ContentCategory.HTML, sniff("<head><title>t</title></head>").category());
        assertEquals(ContentCategory.HTML, sniff("<body>").category());
    }

    @Test
    public void skipsBomWhitespaceXmlDeclarationAndComments() {
        byte[] body = "  \n<?xml version=\"1.0\"?>\n<!-- generated -->\n<html xmlns=\"http://www.w3.org/1999/xhtml\">".getBytes(UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        assertEquals(ContentCategory.HTML, detector.detect(null, null, withBom).category());
    }

    @Test
    public void sniffsWhenExtensionAndMimeHintAreInconclusive() {
        byte[] html = "<!doctype html><p>x</p>".getBytes(UTF_8);
        assertEquals(ContentCategory.HTML, detector.detect("download.bin", "application/octet-stream", html).category());
        assertEquals(ContentCategory.HTML, detector.detect("page", null, html).category());
    }

    @Test
    public void explicitHintsStillWinOverSniffing() {
        byte[] html = "<!doctype html><p>x</p>".getBytes(UTF_8);
        assertEquals(ContentCategory.PLAIN_TEXT, detector.detect("source.txt", null, html).category());
        assertEquals(ContentCategory.PLAIN_TEXT, detector.detect(null, "text/plain", html).category());
        assertEquals(ContentCategory.STRUCTURED_DATA, detector.detect("feed.xml", null, html).category());
    }

    @Test
    public void pdfMagicStillWins() {
        assertEquals(ContentCategory.PDF, sniff("%PDF-1.7 <html>").category());
    }

    @Test
    public void doesNotMistakeLookalikesForHtml() {
        assertEquals(ContentCategory.UNKNOWN, sniff("hello <html>").category());
        assertEquals(ContentCategory.UNKNOWN, sniff("<header>").category());
        assertEquals(ContentCategory.UNKNOWN, sniff("<htmlx>").category());
        assertEquals(ContentCategory.UNKNOWN, sniff("<!-- never closed <html>").category());
        assertEquals(ContentCategory.UNKNOWN, sniff("<?xml version=\"1.0\"?><note/>").category());
        assertEquals(ContentCategory.UNKNOWN, detector.detect(null, null, new byte[0]).category());
    }
}
