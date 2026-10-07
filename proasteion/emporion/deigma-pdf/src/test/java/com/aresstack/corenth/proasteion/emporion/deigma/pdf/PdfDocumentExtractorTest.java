package com.aresstack.corenth.proasteion.emporion.deigma.pdf;

import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfFixtures.PDF;
import static com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfFixtures.pages;
import static com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfFixtures.protectedWith;
import static com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfFixtures.ref;
import static com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfFixtures.request;
import static com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfFixtures.withInformation;
import static org.junit.Assert.*;

/**
 * Tests for {@link PdfDocumentExtractor}.
 */
public class PdfDocumentExtractorTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final PdfDocumentExtractor extractor = new PdfDocumentExtractor();

    private ExtractionResult extract(byte[] pdf) {
        return extractor.extract(request(pdf));
    }

    private ExtractedDocument document(byte[] pdf) {
        ExtractionResult result = extract(pdf);
        assertTrue(result.errorMessage(), result.isSuccess());
        return result.document();
    }

    private static List<String> describe(ExtractedDocument document) {
        List<String> lines = new ArrayList<String>();
        for (ExtractedBlock block : document.blocks()) {
            lines.add(block.kind() + "|" + block.text() + "|" + block.attributes());
        }
        return lines;
    }

    private static List<ExtractedBlock> textBlocks(ExtractedDocument document) {
        List<ExtractedBlock> blocks = new ArrayList<ExtractedBlock>();
        for (ExtractedBlock block : document.blocks()) {
            if (block.kind() == BlockKind.TEXT) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    // ── supports() ───────────────────────────────────────────────────────────

    @Test
    public void supportsPdfOnly() {
        assertTrue(extractor.supports(PDF));
        assertFalse(extractor.supports(new DetectedContentType("text/html", ContentCategory.HTML, null)));
        assertFalse(extractor.supports(new DetectedContentType("application/msword", ContentCategory.OFFICE_DOCUMENT, null)));
        assertFalse(extractor.supports(null));
    }

    // ── Pages and metadata ───────────────────────────────────────────────────

    @Test
    public void mapsEachPageToOneTextBlockInPageOrder() {
        ExtractedDocument document = document(pages("Erste Seite\nzweite Zeile", "Zweite Seite", "Dritte Seite"));
        assertEquals(Arrays.asList(
                "METADATA|null|{name=pages, value=3}",
                "TEXT|Erste Seite\nzweite Zeile|{page=1}",
                "TEXT|Zweite Seite|{page=2}",
                "TEXT|Dritte Seite|{page=3}"), describe(document));
        assertEquals("Erste Seite\nzweite Zeile\nZweite Seite\nDritte Seite", document.combinedText());
    }

    @Test
    public void blankPagesYieldNoBlockButKeepPageNumbers() {
        ExtractedDocument document = document(pages("Vorne", "", "Hinten"));
        List<ExtractedBlock> text = textBlocks(document);
        assertEquals(2, text.size());
        assertEquals("1", text.get(0).attributes().get("page"));
        assertEquals("3", text.get(1).attributes().get("page"));
    }

    @Test
    public void mapsInformationDictionary() {
        ExtractedDocument document = document(withInformation("Inhalt"));
        assertEquals("Hafenbericht", document.title());
        assertSame(PDF, document.contentType());
        assertEquals(Arrays.asList(
                "METADATA|null|{name=author, value=Corenth Tests}",
                "METADATA|null|{name=subject, value=Deigma}",
                "METADATA|null|{name=keywords, value=hafen, fracht}",
                "METADATA|null|{name=creator, value=PdfFixtures}",
                "METADATA|null|{name=producer, value=PDFBox}",
                "METADATA|null|{name=created, value=2026-10-07T01:02:03Z}",
                "METADATA|null|{name=pages, value=1}",
                "TEXT|Inhalt|{page=1}"), describe(document));
    }

    @Test
    public void metadataStaysOutOfCombinedText() {
        assertEquals("Inhalt", document(withInformation("Inhalt")).combinedText());
    }

    @Test
    public void decodesWinAnsiText() {
        assertEquals("Grüße aus Korinth", textBlocks(document(pages("Grüße aus Korinth"))).get(0).text());
    }

    @Test
    public void blockIndexesAreConsecutive() {
        List<ExtractedBlock> blocks = document(withInformation("A", "B")).blocks();
        for (int i = 0; i < blocks.size(); i++) {
            assertEquals(i, blocks.get(i).index());
        }
    }

    @Test
    public void extractionIsDeterministic() {
        byte[] pdf = withInformation("Eins", "Zwei");
        assertEquals(describe(document(pdf)), describe(document(pdf)));
    }

    @Test
    public void documentWithoutTextWarnsInsteadOfFailing() {
        ExtractionResult result = extract(pages("", ""));
        assertTrue(result.isSuccess());
        assertTrue(textBlocks(result.document()).isEmpty());
        assertEquals(Arrays.asList("METADATA|null|{name=pages, value=2}"), describe(result.document()));
        assertTrue(result.warnings().get(0).startsWith("NO_TEXT_LAYER"));
    }

    // ── Encryption ───────────────────────────────────────────────────────────

    @Test
    public void userPasswordYieldsEncryptedFailure() {
        ExtractionResult result = extract(protectedWith("owner", "user", true, "Geheim"));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("PDF_ENCRYPTED"));
        assertSame(PDF, result.detectedType());
    }

    @Test
    public void ownerPasswordOnlyIsExtractedWithWarning() {
        ExtractionResult result = extract(protectedWith("owner", "", true, "Lesbar"));
        assertTrue(result.errorMessage(), result.isSuccess());
        assertEquals("Lesbar", textBlocks(result.document()).get(0).text());
        assertTrue(result.warnings().get(0).startsWith("PDF_OWNER_PASSWORD_PROTECTED"));
    }

    @Test
    public void forbiddenExtractionIsRespected() {
        ExtractionResult result = extract(protectedWith("owner", "", false, "Gesperrt"));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("PDF_EXTRACTION_NOT_PERMITTED"));
    }

    // ── Corrupt and unsupported input ────────────────────────────────────────

    @Test
    public void corruptBytesYieldParseFailure() {
        ExtractionResult result = extract("%PDF-1.4 this is not a real document".getBytes(UTF_8));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("PDF_PARSE_FAILED"));
    }

    @Test
    public void emptyBytesYieldParseFailure() {
        ExtractionResult result = extract(new byte[0]);
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().startsWith("PDF_PARSE_FAILED"));
    }

    @Test
    public void truncatedDocumentNeverThrows() {
        byte[] pdf = pages("Abgeschnitten");
        ExtractionResult result = extract(Arrays.copyOf(pdf, pdf.length / 2));
        assertTrue(result.isSuccess() || result.errorMessage().startsWith("PDF_PARSE_FAILED"));
    }

    @Test
    public void rejectsContentAboveTheSizeLimit() {
        byte[] pdf = pages("x");
        ExtractionResult result = new PdfDocumentExtractor(pdf.length - 1, PdfDocumentLoader.PDFBOX).extract(request(pdf));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().startsWith("CONTENT_TOO_LARGE"));
    }

    @Test
    public void rejectsRequestDetectedAsAnotherType() {
        DetectedContentType html = new DetectedContentType("text/html", ContentCategory.HTML, "x.html");
        ExtractionResult result = extractor.extract(new ExtractionRequest(ref(), pages("x"), "x.html", null, html));
        assertFalse(result.isSuccess());
        assertSame(html, result.detectedType());
        assertTrue(result.errorMessage().startsWith("UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    public void createsPdfTypeWhenRequestCarriesNoDetectedType() {
        ExtractionResult result = extractor.extract(new ExtractionRequest(ref(), pages("x"), "scan.pdf", null));
        assertTrue(result.isSuccess());
        assertEquals("application/pdf", result.detectedType().mimeType());
        assertEquals("scan.pdf", result.detectedType().filenameHint());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeSizeLimit() {
        new PdfDocumentExtractor(-1, PdfDocumentLoader.PDFBOX);
    }

    // ── Resource cleanup ─────────────────────────────────────────────────────

    @Test
    public void closesTheDocumentAfterSuccess() {
        RecordingLoader loader = new RecordingLoader();
        assertTrue(new PdfDocumentExtractor(PdfDocumentExtractor.DEFAULT_MAX_CONTENT_BYTES, loader)
                .extract(request(pages("x"))).isSuccess());
        assertTrue(loader.loaded.getDocument().isClosed());
    }

    @Test
    public void closesTheDocumentAfterFailure() {
        RecordingLoader loader = new RecordingLoader();
        assertFalse(new PdfDocumentExtractor(PdfDocumentExtractor.DEFAULT_MAX_CONTENT_BYTES, loader)
                .extract(request(protectedWith("owner", "", false, "x"))).isSuccess());
        assertTrue(loader.loaded.getDocument().isClosed());
    }

    @Test
    public void closesTheDocumentWhenExtractionThrows() {
        PdfDocumentLoader failing = new PdfDocumentLoader() {
            @Override
            public PDDocument load(byte[] content) throws IOException {
                final PDDocument document = PDDocument.load(content);
                return new PDDocument(document.getDocument()) {
                    @Override
                    public int getNumberOfPages() {
                        throw new IllegalStateException("broken page tree");
                    }
                };
            }
        };
        RecordingLoader loader = new RecordingLoader(failing);
        ExtractionResult result = new PdfDocumentExtractor(PdfDocumentExtractor.DEFAULT_MAX_CONTENT_BYTES, loader)
                .extract(request(pages("x")));
        assertFalse(result.isSuccess());
        assertEquals("PDF_PARSE_FAILED: broken page tree", result.errorMessage());
        assertTrue(loader.loaded.getDocument().isClosed());
    }

    private static final class RecordingLoader implements PdfDocumentLoader {

        private final PdfDocumentLoader delegate;
        private PDDocument loaded;

        RecordingLoader() {
            this(PdfDocumentLoader.PDFBOX);
        }

        RecordingLoader(PdfDocumentLoader delegate) {
            this.delegate = delegate;
        }

        @Override
        public PDDocument load(byte[] content) throws IOException {
            loaded = delegate.load(content);
            return loaded;
        }
    }
}
