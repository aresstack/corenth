package com.aresstack.corenth.proasteion.emporion.deigma.office;

import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts sheets as tab-separated tables from XLSX resources in workbook order.
 *
 * <p>Each sheet with content becomes one {@code TABLE} block with the attributes
 * {@code sheet} (name), {@code sheetIndex} (0-based), {@code rows}, {@code columns} and, for
 * hidden sheets, {@code hidden}. Cells are rendered with their display format; formula cells
 * show their cached result, never the formula text and never a recalculated value, because
 * Deigma extracts rather than computes. Columns are absolute from column A, so interior
 * empty cells stay as empty fields; trailing empty cells and fully empty rows are omitted.
 * Core properties map to the title and text-less {@code METADATA} blocks, plus a sheet count.
 *
 * <p>Failures are results, never exceptions: {@code UNSUPPORTED_CONTENT_TYPE},
 * {@code CONTENT_TOO_LARGE}, {@code OFFICE_ENCRYPTED} (no password is requested),
 * {@code UNSUPPORTED_FORMAT} (legacy binary {@code .xls}) or {@code OFFICE_PARSE_FAILED}.
 * The parsed workbook is closed on every path. The Office library is an implementation
 * detail; no library type appears in this class's signatures.
 */
public final class XlsxDocumentExtractor implements ResourceExtractor {

    /** MIME type of SpreadsheetML workbooks. */
    public static final String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Largest input this extractor accepts; larger inputs yield {@code CONTENT_TOO_LARGE}. */
    public static final long DEFAULT_MAX_CONTENT_BYTES = OfficeExtraction.DEFAULT_MAX_CONTENT_BYTES;

    private static final OfficeDocumentLoader<XSSFWorkbook> POI_LOADER = new OfficeDocumentLoader<XSSFWorkbook>() {
        @Override
        public XSSFWorkbook load(byte[] content) throws IOException {
            return new XSSFWorkbook(new ByteArrayInputStream(content));
        }
    };

    private final long maxContentBytes;
    private final OfficeDocumentLoader<XSSFWorkbook> loader;

    public XlsxDocumentExtractor() {
        this(DEFAULT_MAX_CONTENT_BYTES, POI_LOADER);
    }

    XlsxDocumentExtractor(long maxContentBytes, OfficeDocumentLoader<XSSFWorkbook> loader) {
        if (maxContentBytes < 0) {
            throw new IllegalArgumentException("Maximum content size must not be negative");
        }
        if (loader == null) {
            throw new IllegalArgumentException("Loader must not be null");
        }
        this.maxContentBytes = maxContentBytes;
        this.loader = loader;
    }

    static OfficeDocumentLoader<XSSFWorkbook> poiLoader() {
        return POI_LOADER;
    }

    @Override
    public boolean supports(DetectedContentType contentType) {
        return contentType != null && contentType.category() == ContentCategory.OFFICE_DOCUMENT
                && XLSX_MIME_TYPE.equals(contentType.mimeType());
    }

    @Override
    public ExtractionResult extract(final ExtractionRequest request) {
        final DetectedContentType type = request.detectedContentType() != null
                ? request.detectedContentType()
                : new DetectedContentType(XLSX_MIME_TYPE, ContentCategory.OFFICE_DOCUMENT, request.filenameHint());
        if (!supports(type)) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "UNSUPPORTED_CONTENT_TYPE: " + type.mimeType() + " is not XLSX");
        }
        final List<String> warnings = new ArrayList<String>();
        return OfficeExtraction.extract(request, type, maxContentBytes, "XLSX", loader,
                new OfficeExtraction.Mapper<XSSFWorkbook>() {
                    @Override
                    public ExtractionResult map(XSSFWorkbook workbook, ExtractedDocument.Builder document, List<String> warnings) {
                        OfficeExtraction.BlockSequence blocks = new OfficeExtraction.BlockSequence(document);
                        OfficeExtraction.addCoreProperties(workbook.getProperties(), document, blocks);
                        blocks.metadata("sheets", String.valueOf(workbook.getNumberOfSheets()));
                        DataFormatter formatter = new DataFormatter(Locale.ROOT);
                        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                            addSheet(workbook, index, formatter, blocks);
                        }
                        if (blocks.visibleBlocks() == 0) {
                            warnings.add("NO_VISIBLE_TEXT: the XLSX workbook contains no cell values");
                        }
                        return ExtractionResult.successWithWarnings(request.resourceRef(), type, document.build(), warnings);
                    }
                }, warnings);
    }

    private static void addSheet(XSSFWorkbook workbook, int index, DataFormatter formatter,
                                 OfficeExtraction.BlockSequence blocks) {
        Sheet sheet = workbook.getSheetAt(index);
        List<String> rows = new ArrayList<String>();
        int columns = 0;
        for (Row row : sheet) {
            List<String> cells = new ArrayList<String>();
            int lastFilled = -1;
            for (int column = 0; column < Math.max(0, row.getLastCellNum()); column++) {
                Cell cell = row.getCell(column);
                String value = cell == null ? "" : OfficeExtraction.collapseWhitespace(cellText(cell, formatter));
                cells.add(value);
                if (!value.isEmpty()) {
                    lastFilled = column;
                }
            }
            if (lastFilled >= 0) {
                List<String> filled = cells.subList(0, lastFilled + 1);
                rows.add(OfficeExtraction.join(filled, '\t'));
                columns = Math.max(columns, filled.size());
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("sheet", sheet.getSheetName());
        attributes.put("sheetIndex", String.valueOf(index));
        attributes.put("rows", String.valueOf(rows.size()));
        attributes.put("columns", String.valueOf(columns));
        if (workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index)) {
            attributes.put("hidden", "true");
        }
        blocks.visible(BlockKind.TABLE, OfficeExtraction.join(rows, '\n'), attributes);
    }

    /** Renders the displayed value; formula cells use their cached result instead of the formula. */
    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell.getCellType() != CellType.FORMULA) {
            return formatter.formatCellValue(cell);
        }
        switch (cell.getCachedFormulaResultType()) {
            case NUMERIC:
                CellStyle style = cell.getCellStyle();
                return formatter.formatRawCellContents(cell.getNumericCellValue(),
                        style.getDataFormat(), style.getDataFormatString());
            case STRING:
                return cell.getRichStringCellValue().getString();
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case ERROR:
                return FormulaError.forInt(cell.getErrorCellValue()).getString();
            default:
                return "";
        }
    }
}
