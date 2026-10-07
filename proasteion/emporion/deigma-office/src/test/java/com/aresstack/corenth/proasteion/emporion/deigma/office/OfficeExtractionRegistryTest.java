package com.aresstack.corenth.proasteion.emporion.deigma.office;

import com.aresstack.corenth.proasteion.emporion.deigma.ContentDetector;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import org.junit.Test;

import static com.aresstack.corenth.proasteion.emporion.deigma.office.OfficeFixtures.ref;
import static org.junit.Assert.*;

/**
 * Detection → registry selection → extraction through the existing Deigma contracts only.
 */
public class OfficeExtractionRegistryTest {

    private final ContentDetector detector = new SimpleContentDetector();
    private final ExtractionRegistry registry = new ExtractionRegistry();

    public OfficeExtractionRegistryTest() {
        registry.register(new PlainTextExtractor());
        registry.register(new DocxDocumentExtractor());
        registry.register(new XlsxDocumentExtractor());
    }

    private ExtractionResult detectAndExtract(String filename, byte[] content) {
        DetectedContentType type = detector.detect(filename, null, null);
        ResourceExtractor extractor = registry.findExtractor(type);
        assertNotNull("no extractor for " + type, extractor);
        return extractor.extract(new ExtractionRequest(ref(), content, filename, null, type));
    }

    @Test
    public void selectsDocxAndXlsxExtractorsByExtension() {
        assertTrue(registry.findExtractor(detector.detect("bericht.docx", null, null)) instanceof DocxDocumentExtractor);
        assertTrue(registry.findExtractor(detector.detect("zoelle.xlsx", null, null)) instanceof XlsxDocumentExtractor);
    }

    @Test
    public void selectsByMimeHint() {
        assertTrue(registry.findExtractor(detector.detect(null, DocxDocumentExtractor.DOCX_MIME_TYPE, null))
                instanceof DocxDocumentExtractor);
        assertTrue(registry.findExtractor(detector.detect(null, XlsxDocumentExtractor.XLSX_MIME_TYPE, null))
                instanceof XlsxDocumentExtractor);
    }

    @Test
    public void legacyAndOtherOfficeFormatsFindNoExtractor() {
        assertNull(registry.findExtractor(detector.detect("alt.doc", null, null)));
        assertNull(registry.findExtractor(detector.detect("alt.xls", null, null)));
        assertNull(registry.findExtractor(detector.detect("folien.pptx", null, null)));
        assertNull(registry.findExtractor(detector.detect("text.odt", null, null)));
    }

    @Test
    public void endToEndExtraction() {
        ExtractionResult docx = detectAndExtract("bericht.docx", OfficeFixtures.reportDocx());
        assertTrue(docx.isSuccess());
        assertTrue(docx.document().combinedText().startsWith("Ankunft im Hafen"));
        ExtractionResult xlsx = detectAndExtract("zoelle.xlsx", XlsxFixtures.workbook());
        assertTrue(xlsx.isSuccess());
        assertTrue(xlsx.document().combinedText().startsWith("Ware\tZoll"));
    }
}
