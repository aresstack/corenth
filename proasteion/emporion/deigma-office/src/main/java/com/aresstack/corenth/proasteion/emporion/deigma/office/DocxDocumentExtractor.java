package com.aresstack.corenth.proasteion.emporion.deigma.office;

import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts paragraphs, headings, list items and tables from DOCX resources in body order.
 *
 * <p>Paragraphs whose style is a built-in heading ({@code heading 1} … {@code heading 9})
 * become {@code HEADING} blocks with a {@code level}; numbered or bulleted paragraphs become
 * {@code LIST} blocks with {@code ordered} and {@code depth}; other paragraphs become
 * {@code TEXT}; tables become one {@code TABLE} block with tab-separated cells and
 * newline-separated rows. Core properties map to the title and text-less {@code METADATA}
 * blocks; each embedded object is listed as an {@code embedded} metadata entry with its part
 * name and content type, without being extracted. Headers, footers, footnotes, comments and
 * images are not extracted.
 *
 * <p>Failures are results, never exceptions: {@code UNSUPPORTED_CONTENT_TYPE},
 * {@code CONTENT_TOO_LARGE}, {@code OFFICE_ENCRYPTED} (no password is requested),
 * {@code UNSUPPORTED_FORMAT} (legacy binary {@code .doc}) or {@code OFFICE_PARSE_FAILED}.
 * The parsed document is closed on every path. The Office library is an implementation
 * detail; no library type appears in this class's signatures.
 */
public final class DocxDocumentExtractor implements ResourceExtractor {

    /** MIME type of WordprocessingML documents. */
    public static final String DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Largest input this extractor accepts; larger inputs yield {@code CONTENT_TOO_LARGE}. */
    public static final long DEFAULT_MAX_CONTENT_BYTES = OfficeExtraction.DEFAULT_MAX_CONTENT_BYTES;

    private static final Pattern HEADING_STYLE = Pattern.compile("heading ([1-9])");

    private static final OfficeDocumentLoader<XWPFDocument> POI_LOADER = new OfficeDocumentLoader<XWPFDocument>() {
        @Override
        public XWPFDocument load(byte[] content) throws IOException {
            return new XWPFDocument(new ByteArrayInputStream(content));
        }
    };

    private final long maxContentBytes;
    private final OfficeDocumentLoader<XWPFDocument> loader;

    public DocxDocumentExtractor() {
        this(DEFAULT_MAX_CONTENT_BYTES, POI_LOADER);
    }

    DocxDocumentExtractor(long maxContentBytes, OfficeDocumentLoader<XWPFDocument> loader) {
        if (maxContentBytes < 0) {
            throw new IllegalArgumentException("Maximum content size must not be negative");
        }
        if (loader == null) {
            throw new IllegalArgumentException("Loader must not be null");
        }
        this.maxContentBytes = maxContentBytes;
        this.loader = loader;
    }

    static OfficeDocumentLoader<XWPFDocument> poiLoader() {
        return POI_LOADER;
    }

    @Override
    public boolean supports(DetectedContentType contentType) {
        return contentType != null && contentType.category() == ContentCategory.OFFICE_DOCUMENT
                && DOCX_MIME_TYPE.equals(contentType.mimeType());
    }

    @Override
    public ExtractionResult extract(final ExtractionRequest request) {
        final DetectedContentType type = request.detectedContentType() != null
                ? request.detectedContentType()
                : new DetectedContentType(DOCX_MIME_TYPE, ContentCategory.OFFICE_DOCUMENT, request.filenameHint());
        if (!supports(type)) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "UNSUPPORTED_CONTENT_TYPE: " + type.mimeType() + " is not DOCX");
        }
        final List<String> warnings = new ArrayList<String>();
        return OfficeExtraction.extract(request, type, maxContentBytes, "DOCX", loader,
                new OfficeExtraction.Mapper<XWPFDocument>() {
                    @Override
                    public ExtractionResult map(XWPFDocument docx, ExtractedDocument.Builder document, List<String> warnings)
                            throws IOException {
                        OfficeExtraction.BlockSequence blocks = new OfficeExtraction.BlockSequence(document);
                        OfficeExtraction.addCoreProperties(docx.getProperties(), document, blocks);
                        addEmbeddedObjects(docx, blocks);
                        for (IBodyElement element : docx.getBodyElements()) {
                            if (element instanceof XWPFParagraph) {
                                addParagraph(docx, (XWPFParagraph) element, blocks);
                            } else if (element instanceof XWPFTable) {
                                addTable((XWPFTable) element, blocks);
                            }
                        }
                        if (blocks.visibleBlocks() == 0) {
                            warnings.add("NO_VISIBLE_TEXT: the DOCX document contains no body text");
                        }
                        return ExtractionResult.successWithWarnings(request.resourceRef(), type, document.build(), warnings);
                    }
                }, warnings);
    }

    private static void addEmbeddedObjects(XWPFDocument docx, OfficeExtraction.BlockSequence blocks) {
        List<PackagePart> parts;
        try {
            parts = docx.getAllEmbeddedParts();
        } catch (OpenXML4JException e) {
            return;
        }
        for (PackagePart part : parts) {
            blocks.metadata("embedded", part.getPartName().getName() + " (" + part.getContentType() + ")");
        }
    }

    private static void addParagraph(XWPFDocument docx, XWPFParagraph paragraph, OfficeExtraction.BlockSequence blocks) {
        String text = OfficeExtraction.collapseWhitespace(paragraph.getText());
        if (text.isEmpty()) {
            return;
        }
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        int headingLevel = headingLevel(docx, paragraph);
        if (headingLevel > 0) {
            attributes.put("level", String.valueOf(headingLevel));
            blocks.visible(BlockKind.HEADING, text, attributes);
        } else if (paragraph.getNumID() != null) {
            String format = paragraph.getNumFmt();
            BigInteger level = paragraph.getNumIlvl();
            attributes.put("ordered", String.valueOf(format != null && !"bullet".equals(format)));
            attributes.put("depth", String.valueOf(level == null ? 1 : level.intValue() + 1));
            blocks.visible(BlockKind.LIST, text, attributes);
        } else {
            blocks.visible(BlockKind.TEXT, text, null);
        }
    }

    private static int headingLevel(XWPFDocument docx, XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null || docx.getStyles() == null) {
            return 0;
        }
        XWPFStyle style = docx.getStyles().getStyle(styleId);
        String name = style != null && style.getName() != null ? style.getName() : styleId;
        Matcher matcher = HEADING_STYLE.matcher(name.toLowerCase(Locale.ROOT).replace("heading", "heading ").replaceAll("\\s+", " "));
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static void addTable(XWPFTable table, OfficeExtraction.BlockSequence blocks) {
        List<String> rows = new ArrayList<String>();
        int columns = 0;
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<String>();
            boolean hasText = false;
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = OfficeExtraction.collapseWhitespace(cell.getText());
                hasText |= !text.isEmpty();
                cells.add(text);
            }
            if (hasText) {
                rows.add(OfficeExtraction.join(cells, '\t'));
                columns = Math.max(columns, cells.size());
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("rows", String.valueOf(rows.size()));
        attributes.put("columns", String.valueOf(columns));
        blocks.visible(BlockKind.TABLE, OfficeExtraction.join(rows, '\n'), attributes);
    }
}
