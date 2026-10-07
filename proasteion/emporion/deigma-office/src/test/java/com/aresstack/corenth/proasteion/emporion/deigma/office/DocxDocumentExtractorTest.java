package com.aresstack.corenth.proasteion.emporion.deigma.office;

import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.DOCX;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.UTF_8;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.XLSX;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.describe;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.docxWithBody;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.encrypt;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.legacyOle2;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.paragraph;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.ref;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.reportDocx;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.request;
import static org.junit.Assert.*;

/**
 * Tests for {@link DocxDocumentExtractor}.
 */
public class DocxDocumentExtractorTest {

    private final DocxDocumentExtractor extractor = new DocxDocumentExtractor();

    private ExtractionResult extract(byte[] docx) {
        return extractor.extract(request(docx, DOCX));
    }

    private ExtractedDocument document(byte[] docx) {
        ExtractionResult result = extract(docx);
        assertTrue(result.errorMessage(), result.isSuccess());
        return result.document();
    }

    @Test
    public void supportsDocxOnly() {
        assertTrue(extractor.supports(DOCX));
        assertFalse(extractor.supports(XLSX));
        assertFalse(extractor.supports(new DetectedContentType("application/msword", ContentCategory.OFFICE_DOCUMENT, "x.doc")));
        assertFalse(extractor.supports(new DetectedContentType("application/pdf", ContentCategory.PDF, null)));
        assertFalse(extractor.supports(null));
    }

    @Test
    public void mapsBodyElementsInOrder() {
        ExtractedDocument document = document(reportDocx());
        assertEquals("Hafenbericht", document.title());
        assertSame(DOCX, document.contentType());
        assertEquals(Arrays.asList(
                "METADATA|null|{name=author, value=Corenth Tests}",
                "METADATA|null|{name=subject, value=Deigma}",
                "METADATA|null|{name=keywords, value=hafen, fracht}",
                "METADATA|null|{name=created, value=2026-10-07T01:02:03Z}",
                "METADATA|null|{name=embedded, value=/word/embeddings/Tabelle.xlsx ("
                        + XlsxDocumentExtractor.XLSX_MIME_TYPE + ")}",
                "HEADING|Ankunft im Hafen|{level=1}",
                "TEXT|Die Holkas liefert Fracht.|{}",
                "HEADING|Ladung|{level=2}",
                "LIST|Bronze|{ordered=false, depth=1}",
                "LIST|Wein|{ordered=false, depth=1}",
                "LIST|Rotwein|{ordered=false, depth=2}",
                "LIST|Erster Schritt|{ordered=true, depth=1}",
                "TABLE|Ware\tZoll\nBronze\t5\nWein\t|{rows=3, columns=2}",
                "TEXT|Ende.|{}"), describe(document));
    }

    @Test
    public void metadataAndEmbeddedPartsStayOutOfCombinedText() {
        String text = document(reportDocx()).combinedText();
        assertTrue(text.startsWith("Ankunft im Hafen\n"));
        assertFalse(text.contains("Corenth Tests"));
        assertFalse(text.contains("Tabelle.xlsx"));
        assertFalse(text.contains("not inspected"));
    }

    @Test
    public void blockIndexesAreConsecutiveAndExtractionIsDeterministic() {
        byte[] docx = reportDocx();
        List<ExtractedBlock> blocks = document(docx).blocks();
        for (int i = 0; i < blocks.size(); i++) {
            assertEquals(i, blocks.get(i).index());
        }
        assertEquals(describe(document(docx)), describe(document(docx)));
    }

    @Test
    public void nonHeadingStylesStayText() {
        ExtractedDocument document = document(docxWithBody(
                "<w:p><w:pPr><w:pStyle w:val=\"Quote\"/></w:pPr><w:r><w:t>Zitat</w:t></w:r></w:p>"));
        assertEquals(Arrays.asList("TEXT|Zitat|{}"), describe(document));
        assertNull(document.title());
    }

    @Test
    public void emptyBodyWarnsInsteadOfFailing() {
        ExtractionResult result = extract(docxWithBody(paragraph("")));
        assertTrue(result.isSuccess());
        assertTrue(result.document().blocks().isEmpty());
        assertTrue(result.warnings().get(0).startsWith("NO_VISIBLE_TEXT"));
    }

    @Test
    public void passwordProtectedDocumentYieldsEncryptedFailure() {
        ExtractionResult result = extract(encrypt(reportDocx(), "geheim"));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("OFFICE_ENCRYPTED"));
    }

    @Test
    public void legacyBinaryDocumentIsUnsupported() {
        ExtractionResult result = extract(legacyOle2());
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("UNSUPPORTED_FORMAT"));
    }

    @Test
    public void corruptBytesYieldParseFailure() {
        ExtractionResult result = extract("PK\u0003\u0004 broken".getBytes(UTF_8));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("OFFICE_PARSE_FAILED"));
    }

    @Test
    public void emptyBytesYieldParseFailure() {
        ExtractionResult result = extract(new byte[0]);
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("OFFICE_PARSE_FAILED"));
    }

    @Test
    public void workbookBytesAreNotADocx() {
        ExtractionResult result = extract(XlsxFixtures.workbook());
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("OFFICE_PARSE_FAILED"));
    }

    @Test
    public void rejectsContentAboveTheSizeLimit() {
        byte[] docx = reportDocx();
        ExtractionResult result = new DocxDocumentExtractor(docx.length - 1, DocxDocumentExtractor.poiLoader())
                .extract(request(docx, DOCX));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().startsWith("CONTENT_TOO_LARGE"));
    }

    @Test
    public void rejectsRequestDetectedAsAnotherType() {
        ExtractionResult result = extractor.extract(request(reportDocx(), XLSX));
        assertFalse(result.isSuccess());
        assertSame(XLSX, result.detectedType());
        assertTrue(result.errorMessage().startsWith("UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    public void createsDocxTypeWhenRequestCarriesNoDetectedType() {
        ExtractionResult result = extractor.extract(new ExtractionRequest(ref(), reportDocx(), "a.docx", null));
        assertTrue(result.isSuccess());
        assertEquals(DocxDocumentExtractor.DOCX_MIME_TYPE, result.detectedType().mimeType());
    }

    @Test
    public void closesTheDocumentAfterSuccess() {
        CountingLoader loader = new CountingLoader(false);
        assertTrue(new DocxDocumentExtractor(DocxDocumentExtractor.DEFAULT_MAX_CONTENT_BYTES, loader)
                .extract(request(reportDocx(), DOCX)).isSuccess());
        assertEquals(1, loader.closed);
    }

    @Test
    public void closesTheDocumentWhenMappingThrows() {
        CountingLoader loader = new CountingLoader(true);
        ExtractionResult result = new DocxDocumentExtractor(DocxDocumentExtractor.DEFAULT_MAX_CONTENT_BYTES, loader)
                .extract(request(reportDocx(), DOCX));
        assertEquals("OFFICE_PARSE_FAILED: broken body", result.errorMessage());
        assertEquals(1, loader.closed);
    }

    private static final class CountingLoader implements OfficeDocumentLoader<XWPFDocument> {

        private final boolean breakBody;
        private int closed;

        CountingLoader(boolean breakBody) {
            this.breakBody = breakBody;
        }

        @Override
        public XWPFDocument load(byte[] content) throws IOException {
            return new XWPFDocument(new ByteArrayInputStream(content)) {
                @Override
                public void close() throws IOException {
                    closed++;
                    super.close();
                }

                @Override
                public List<IBodyElement> getBodyElements() {
                    if (breakBody) {
                        throw new IllegalStateException("broken body");
                    }
                    return super.getBodyElements();
                }
            };
        }
    }
}
