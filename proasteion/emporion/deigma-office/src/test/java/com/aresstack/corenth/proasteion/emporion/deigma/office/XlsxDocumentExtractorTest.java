package com.aresstack.corenth.proasteion.emporion.deigma.office;

import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.DOCX;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.UTF_8;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.XLSX;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.describe;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.encrypt;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.legacyOle2;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.ref;
import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.request;
import static org.junit.Assert.*;

/**
 * Tests for {@link XlsxDocumentExtractor}.
 */
public class XlsxDocumentExtractorTest {

    private final XlsxDocumentExtractor extractor = new XlsxDocumentExtractor();

    private ExtractionResult extract(byte[] xlsx) {
        return extractor.extract(request(xlsx, XLSX));
    }

    private ExtractedDocument document(byte[] xlsx) {
        ExtractionResult result = extract(xlsx);
        assertTrue(result.errorMessage(), result.isSuccess());
        return result.document();
    }

    @Test
    public void supportsXlsxOnly() {
        assertTrue(extractor.supports(XLSX));
        assertFalse(extractor.supports(DOCX));
        assertFalse(extractor.supports(new DetectedContentType("application/vnd.ms-excel", ContentCategory.OFFICE_DOCUMENT, "x.xls")));
        assertFalse(extractor.supports(null));
    }

    @Test
    public void mapsSheetsInWorkbookOrder() {
        ExtractedDocument document = document(XlsxFixtures.workbook());
        assertEquals("Zollliste", document.title());
        assertSame(XLSX, document.contentType());
        assertEquals(Arrays.asList(
                "METADATA|null|{name=author, value=Corenth Tests}",
                "METADATA|null|{name=created, value=" + metadataValue(document, "created") + "}",
                "METADATA|null|{name=sheets, value=3}",
                "TABLE|Ware\tZoll\tDatum\n"
                        + "Bronze\t7\t2026-10-07\n"
                        + "Wein\n"
                        + "\tMitte\n"
                        + "Summe\t10\n"
                        + "Betrag\t1,234.50\n"
                        + "Text\tHafen\n"
                        + "Wahr\tTRUE\n"
                        + "Fehler\t#DIV/0!"
                        + "|{sheet=Zölle, sheetIndex=0, rows=9, columns=3}",
                "TABLE|geheim|{sheet=Versteckt, sheetIndex=2, rows=1, columns=1, hidden=true}"), describe(document));
    }

    @Test
    public void formulaCellsShowTheCachedResultWithoutRecalculation() {
        String table = tables(document(XlsxFixtures.workbook())).get(0).text();
        assertTrue(table.contains("Summe\t10"));
        assertFalse(table.contains("B2*2"));
        assertFalse(table.contains("Summe\t14"));
    }

    @Test
    public void emptyCellsKeepColumnPositionsAndEmptyRowsAreOmitted() {
        String[] rows = tables(document(XlsxFixtures.workbook())).get(0).text().split("\n", -1);
        assertEquals("Wein", rows[2]);
        assertEquals("\tMitte", rows[3]);
        assertEquals(9, rows.length);
    }

    @Test
    public void emptyWorkbookWarnsInsteadOfFailing() {
        ExtractionResult result = extract(XlsxFixtures.emptyWorkbook());
        assertTrue(result.isSuccess());
        assertTrue(tables(result.document()).isEmpty());
        assertTrue(result.warnings().get(0).startsWith("NO_VISIBLE_TEXT"));
    }

    @Test
    public void extractionIsDeterministic() {
        byte[] xlsx = XlsxFixtures.workbook();
        assertEquals(describe(document(xlsx)), describe(document(xlsx)));
    }

    @Test
    public void passwordProtectedWorkbookYieldsEncryptedFailure() {
        ExtractionResult result = extract(encrypt(XlsxFixtures.workbook(), "geheim"));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("OFFICE_ENCRYPTED"));
    }

    @Test
    public void legacyBinaryWorkbookIsUnsupported() {
        ExtractionResult result = extract(legacyOle2());
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().startsWith("UNSUPPORTED_FORMAT"));
    }

    @Test
    public void corruptBytesYieldParseFailure() {
        ExtractionResult result = extract("PK\u0003\u0004 broken".getBytes(UTF_8));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("OFFICE_PARSE_FAILED"));
    }

    @Test
    public void documentBytesAreNotAWorkbook() {
        ExtractionResult result = extract(OfficeFixtures.reportDocx());
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage(), result.errorMessage().startsWith("OFFICE_PARSE_FAILED"));
    }

    @Test
    public void rejectsContentAboveTheSizeLimit() {
        byte[] xlsx = XlsxFixtures.workbook();
        ExtractionResult result = new XlsxDocumentExtractor(xlsx.length - 1, XlsxDocumentExtractor.poiLoader())
                .extract(request(xlsx, XLSX));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().startsWith("CONTENT_TOO_LARGE"));
    }

    @Test
    public void rejectsRequestDetectedAsAnotherType() {
        ExtractionResult result = extractor.extract(request(XlsxFixtures.workbook(), DOCX));
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().startsWith("UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    public void createsXlsxTypeWhenRequestCarriesNoDetectedType() {
        ExtractionResult result = extractor.extract(new ExtractionRequest(ref(), XlsxFixtures.workbook(), "a.xlsx", null));
        assertTrue(result.isSuccess());
        assertEquals(XlsxDocumentExtractor.XLSX_MIME_TYPE, result.detectedType().mimeType());
    }

    @Test
    public void closesTheWorkbookAfterSuccessAndFailure() {
        CountingLoader loader = new CountingLoader();
        XlsxDocumentExtractor counting = new XlsxDocumentExtractor(XlsxDocumentExtractor.DEFAULT_MAX_CONTENT_BYTES, loader);
        assertTrue(counting.extract(request(XlsxFixtures.workbook(), XLSX)).isSuccess());
        assertEquals(1, loader.closed);
        loader.breakSheets = true;
        assertEquals("OFFICE_PARSE_FAILED: broken sheets",
                counting.extract(request(XlsxFixtures.workbook(), XLSX)).errorMessage());
        assertEquals(2, loader.closed);
    }

    private static String metadataValue(ExtractedDocument document, String name) {
        for (ExtractedBlock block : document.blocks()) {
            if (block.kind() == BlockKind.METADATA && name.equals(block.attributes().get("name"))) {
                return block.attributes().get("value");
            }
        }
        return null;
    }

    private static List<ExtractedBlock> tables(ExtractedDocument document) {
        List<ExtractedBlock> tables = new java.util.ArrayList<ExtractedBlock>();
        for (ExtractedBlock block : document.blocks()) {
            if (block.kind() == BlockKind.TABLE) {
                tables.add(block);
            }
        }
        return tables;
    }

    private static final class CountingLoader implements OfficeDocumentLoader<XSSFWorkbook> {

        private int closed;
        private boolean breakSheets;

        @Override
        public XSSFWorkbook load(byte[] content) throws IOException {
            return new XSSFWorkbook(new ByteArrayInputStream(content)) {
                @Override
                public void close() throws IOException {
                    closed++;
                    super.close();
                }

                @Override
                public int getNumberOfSheets() {
                    if (breakSheets) {
                        throw new IllegalStateException("broken sheets");
                    }
                    return super.getNumberOfSheets();
                }
            };
        }
    }
}
