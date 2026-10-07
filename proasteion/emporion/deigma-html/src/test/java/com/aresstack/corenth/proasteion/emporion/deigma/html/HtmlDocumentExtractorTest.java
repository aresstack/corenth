package com.aresstack.corenth.proasteion.emporion.deigma.html;

import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.HTML;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.UTF_8;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.describe;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.fixture;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.metadata;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.ref;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.request;
import static com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlTestSupport.visible;
import static org.junit.Assert.*;

/**
 * Tests for {@link HtmlDocumentExtractor}.
 */
public class HtmlDocumentExtractorTest {

    private final HtmlDocumentExtractor extractor = new HtmlDocumentExtractor();

    private ExtractedDocument extract(ExtractionRequest request) {
        ExtractionResult result = extractor.extract(request);
        assertTrue(result.errorMessage(), result.isSuccess());
        return result.document();
    }

    private ExtractedDocument extract(String html) {
        return extract(request(html));
    }

    // ── supports() ───────────────────────────────────────────────────────────

    @Test
    public void supportsHtmlAndXhtml() {
        assertTrue(extractor.supports(HTML));
        assertTrue(extractor.supports(new DetectedContentType("application/xhtml+xml", ContentCategory.HTML, null)));
    }

    @Test
    public void doesNotSupportOtherCategories() {
        assertFalse(extractor.supports(new DetectedContentType("text/plain", ContentCategory.PLAIN_TEXT, null)));
        assertFalse(extractor.supports(new DetectedContentType("text/markdown", ContentCategory.MARKDOWN, null)));
        assertFalse(extractor.supports(new DetectedContentType("application/xml", ContentCategory.STRUCTURED_DATA, null)));
        assertFalse(extractor.supports(null));
    }

    // ── Fixture mapping ──────────────────────────────────────────────────────

    @Test
    public void mapsArticleFixtureToOrderedBlocks() {
        ExtractedDocument document = extract(request(fixture("article.html"), null));

        assertEquals("Hafenbericht Korinth", document.title());
        assertSame(HTML, document.contentType());
        assertEquals(Arrays.asList(
                "METADATA|null|{name=charset, value=UTF-8}",
                "METADATA|null|{name=language, value=de}",
                "METADATA|null|{name=description, value=Kleines Testdokument für Deigma}",
                "METADATA|null|{name=author, value=Corenth Tests}",
                "HEADING|Ankunft im Hafen|{level=1}",
                "TEXT|Die Holkas liefert Fracht, die Deigma prüft sie.|{}",
                "HEADING|Ladung|{level=2}",
                "LIST|Bronze|{ordered=false, depth=1}",
                "LIST|Wein|{ordered=false, depth=1}",
                "LIST|Rotwein|{ordered=true, depth=2}",
                "LIST|Weißwein|{ordered=true, depth=2}",
                "TABLE|Ware\tZoll\nBronze\t5\nWein\t3|{rows=3, columns=2, caption=Zölle}",
                "CODE|int zoll = 5;\n  return zoll;|{language=java}",
                "TEXT|Zeile eins\nZeile zwei|{}",
                "TEXT|Ende.|{}"), describe(document));
    }

    @Test
    public void blockIndexesFollowDocumentOrder() {
        List<ExtractedBlock> blocks = extract(request(fixture("article.html"), null)).blocks();
        for (int i = 0; i < blocks.size(); i++) {
            assertEquals(i, blocks.get(i).index());
        }
    }

    @Test
    public void extractionIsDeterministic() {
        byte[] html = fixture("article.html");
        ExtractedDocument first = extract(request(html, null));
        ExtractedDocument second = extract(request(html, null));
        assertEquals(describe(first), describe(second));
        assertEquals(first.title(), second.title());
    }

    @Test
    public void excludesScriptStyleAndOtherNonVisibleContent() {
        String text = extract(request(fixture("article.html"), null)).combinedText();
        for (String marker : Arrays.asList("SCRIPT_MARKER", "STYLE_MARKER", "NAV_MARKER", "NOSCRIPT_MARKER",
                "HIDDEN_MARKER", "TEMPLATE_MARKER", "COMMENT_MARKER")) {
            assertFalse(marker + " leaked into " + text, text.contains(marker));
        }
    }

    @Test
    public void metadataStaysOutOfCombinedText() {
        String text = extract(request(fixture("article.html"), null)).combinedText();
        assertFalse(text.contains("Kleines Testdokument"));
        assertFalse(text.contains("UTF-8"));
        assertTrue(text.startsWith("Ankunft im Hafen\n"));
    }

    // ── Structure details ────────────────────────────────────────────────────

    @Test
    public void mapsHeadingLevels() {
        ExtractedDocument document = extract("<h1>A</h1><h3>C</h3><h6>F</h6>");
        List<ExtractedBlock> blocks = visible(document);
        assertEquals(3, blocks.size());
        assertEquals(BlockKind.HEADING, blocks.get(2).kind());
        assertEquals("1", blocks.get(0).attributes().get("level"));
        assertEquals("3", blocks.get(1).attributes().get("level"));
        assertEquals("6", blocks.get(2).attributes().get("level"));
    }

    @Test
    public void listItemTextAroundNestedListKeepsOrder() {
        ExtractedDocument document = extract("<ol><li>vor<ul><li>innen</li></ul>nach</li></ol>");
        assertEquals(Arrays.asList(
                "METADATA|null|{name=charset, value=UTF-8}",
                "LIST|vor|{ordered=true, depth=1}",
                "LIST|innen|{ordered=false, depth=2}",
                "LIST|nach|{ordered=true, depth=1}"), describe(document));
    }

    @Test
    public void nestedTableRowsStayInsideTheirCell() {
        ExtractedDocument document = extract(
                "<table><tr><td>außen</td><td><table><tr><td>innen</td></tr></table></td></tr></table>");
        List<ExtractedBlock> blocks = visible(document);
        assertEquals(1, blocks.size());
        assertEquals(BlockKind.TABLE, blocks.get(0).kind());
        assertEquals("außen\tinnen", blocks.get(0).text());
        assertEquals("1", blocks.get(0).attributes().get("rows"));
    }

    @Test
    public void inlineTextBetweenBlocksBecomesTextBlocks() {
        ExtractedDocument document = extract("<div>vorher<p>mitte</p>nachher</div>");
        assertEquals(Arrays.asList("TEXT|vorher|{}", "TEXT|mitte|{}", "TEXT|nachher|{}"),
                describe(document).subList(1, 4));
    }

    @Test
    public void decodesEntitiesAndNonBreakingSpaces() {
        ExtractedDocument document = extract("<p>A&nbsp;&amp;&nbsp;B &lt;c&gt;</p>");
        assertEquals("A & B <c>", visible(document).get(0).text());
    }

    // ── Malformed and empty input ────────────────────────────────────────────

    @Test
    public void repairsMalformedMarkup() {
        ExtractionResult result = extractor.extract(request(fixture("malformed.html"), null));
        assertTrue(result.isSuccess());
        ExtractedDocument document = result.document();
        assertEquals("Kaputt", document.title());
        assertEquals(Arrays.asList(
                "TEXT|Erster Absatz ohne Ende|{}",
                "TEXT|Zweiter Absatz|{}",
                "LIST|Eins|{ordered=false, depth=1}",
                "LIST|Zwei|{ordered=false, depth=1}",
                "HEADING|Überschrift ohne Ende|{level=3}",
                "HEADING|Letzter Absatz & &unknown; <tag>|{level=3}"), describe(document).subList(1, 7));
    }

    @Test
    public void deeplyNestedMarkupDoesNotExhaustTheStack() {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            html.append("<div><span>");
        }
        html.append("tief");
        ExtractedDocument document = extract(html.toString());
        assertEquals("tief", document.combinedText());
    }

    @Test
    public void emptyContentSucceedsWithoutVisibleBlocks() {
        ExtractionResult result = extractor.extract(request(new byte[0], null));
        assertTrue(result.isSuccess());
        assertNull(result.document().title());
        assertTrue(visible(result.document()).isEmpty());
        assertEquals("", result.document().combinedText());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).startsWith("NO_VISIBLE_TEXT"));
    }

    @Test
    public void markupWithoutVisibleTextWarns() {
        ExtractionResult result = extractor.extract(request(
                "<html><head><title>Nur Titel</title><script>x()</script></head><body> <style>p{}</style> </body></html>"));
        assertTrue(result.isSuccess());
        assertEquals("Nur Titel", result.document().title());
        assertTrue(visible(result.document()).isEmpty());
        assertTrue(result.warnings().get(0).startsWith("NO_VISIBLE_TEXT"));
    }

    @Test
    public void binaryGarbageIsNotAnException() {
        byte[] garbage = new byte[512];
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = (byte) (i * 31 + 7);
        }
        ExtractionResult result = extractor.extract(request(garbage, null));
        assertNotNull(result);
        assertTrue(result.isSuccess() || result.errorMessage().startsWith("HTML_PARSE_FAILED"));
    }

    // ── Encoding ─────────────────────────────────────────────────────────────

    @Test
    public void usesCharsetFromContentTypeHint() {
        byte[] latin1 = "<p>Grüße</p>".getBytes(java.nio.charset.Charset.forName("ISO-8859-1"));
        ExtractedDocument document = extract(request(latin1, "text/html; charset=\"ISO-8859-1\""));
        assertEquals("Grüße", visible(document).get(0).text());
        assertEquals("ISO-8859-1", metadata(document, "charset"));
    }

    @Test
    public void usesCharsetFromMetaDeclarationWithoutHint() {
        byte[] cp1252 = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"></head><body><p>Straße – €</p></body></html>"
                .getBytes(java.nio.charset.Charset.forName("windows-1252"));
        ExtractedDocument document = extract(request(cp1252, "text/html"));
        assertEquals("Straße – €", visible(document).get(0).text());
        assertEquals("windows-1252", metadata(document, "charset"));
    }

    @Test
    public void byteOrderMarkSelectsUtf8() {
        byte[] body = "<p>Grüße</p>".getBytes(UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        ExtractedDocument document = extract(request(withBom, null));
        assertEquals("Grüße", visible(document).get(0).text());
        assertEquals("UTF-8", metadata(document, "charset"));
    }

    @Test
    public void defaultsToUtf8() {
        ExtractedDocument document = extract("<p>Grüße</p>");
        assertEquals("Grüße", visible(document).get(0).text());
        assertEquals("UTF-8", metadata(document, "charset"));
    }

    @Test
    public void unsupportedCharsetHintWarnsAndFallsBack() {
        ExtractionResult result = extractor.extract(request("<p>Grüße</p>".getBytes(UTF_8), "text/html; charset=x-no-such-charset"));
        assertTrue(result.isSuccess());
        assertEquals("Grüße", visible(result.document()).get(0).text());
        assertTrue(result.warnings().get(0).startsWith("UNSUPPORTED_CHARSET_HINT: x-no-such-charset"));
    }

    // ── Typed failures ───────────────────────────────────────────────────────

    @Test
    public void rejectsContentAboveTheSizeLimit() {
        ExtractionResult result = new HtmlDocumentExtractor(8).extract(request("<p>zu lang</p>"));
        assertFalse(result.isSuccess());
        assertSame(HTML, result.detectedType());
        assertTrue(result.errorMessage().startsWith("CONTENT_TOO_LARGE"));
    }

    @Test
    public void acceptsContentAtTheSizeLimit() {
        byte[] html = "<p>ok</p>".getBytes(UTF_8);
        assertTrue(new HtmlDocumentExtractor(html.length).extract(request(html, null)).isSuccess());
    }

    @Test
    public void rejectsRequestDetectedAsAnotherType() {
        DetectedContentType pdf = new DetectedContentType("application/pdf", ContentCategory.PDF, "x.pdf");
        ExtractionResult result = extractor.extract(new ExtractionRequest(ref(), "%PDF-1.4".getBytes(UTF_8), "x.pdf", null, pdf));
        assertFalse(result.isSuccess());
        assertSame(pdf, result.detectedType());
        assertTrue(result.errorMessage().startsWith("UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    public void createsHtmlTypeWhenRequestCarriesNoDetectedType() {
        ExtractionResult result = extractor.extract(new ExtractionRequest(ref(), "<p>x</p>".getBytes(UTF_8), "page.htm", null));
        assertTrue(result.isSuccess());
        assertEquals("text/html", result.detectedType().mimeType());
        assertEquals(ContentCategory.HTML, result.detectedType().category());
        assertEquals("page.htm", result.detectedType().filenameHint());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeSizeLimit() {
        new HtmlDocumentExtractor(-1);
    }
}
