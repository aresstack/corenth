package com.aresstack.corenth.proasteion.emporion.deigma.pdf;

import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Extracts text and document metadata from PDF resources.
 *
 * <p>Each page with text becomes one {@code TEXT} block carrying its 1-based {@code page}
 * attribute, so page boundaries survive for later citations without a layout model. The
 * information-dictionary title becomes {@link ExtractedDocument#title()}; author, subject,
 * keywords, creator, producer, creation and modification time (ISO-8601, UTC) and the page
 * count become text-less {@code METADATA} blocks ahead of the pages.
 *
 * <p>Failures are results, never exceptions. Failure messages start with a stable code:
 * {@code UNSUPPORTED_CONTENT_TYPE}, {@code CONTENT_TOO_LARGE}, {@code PDF_ENCRYPTED} (a user
 * password is required; no password is ever requested), {@code PDF_EXTRACTION_NOT_PERMITTED}
 * or {@code PDF_PARSE_FAILED}. A document that opens without a password but carries an owner
 * password is extracted with a {@code PDF_OWNER_PASSWORD_PROTECTED} warning. The parsed
 * document is closed on every path. No OCR is performed: pages without a text layer yield
 * no block, and a document without any text adds a {@code NO_TEXT_LAYER} warning.
 *
 * <p>The PDF library is an implementation detail of this module; no library type appears in
 * this class's signatures.
 */
public final class PdfDocumentExtractor implements ResourceExtractor {

    /** Largest input this extractor accepts; larger inputs yield {@code CONTENT_TOO_LARGE}. */
    public static final long DEFAULT_MAX_CONTENT_BYTES = 64L * 1024 * 1024;

    private static final String PDF_MIME_TYPE = "application/pdf";

    private final long maxContentBytes;
    private final PdfDocumentLoader loader;

    public PdfDocumentExtractor() {
        this(DEFAULT_MAX_CONTENT_BYTES, PdfDocumentLoader.PDFBOX);
    }

    PdfDocumentExtractor(long maxContentBytes, PdfDocumentLoader loader) {
        if (maxContentBytes < 0) {
            throw new IllegalArgumentException("Maximum content size must not be negative");
        }
        if (loader == null) {
            throw new IllegalArgumentException("Loader must not be null");
        }
        this.maxContentBytes = maxContentBytes;
        this.loader = loader;
    }

    @Override
    public boolean supports(DetectedContentType contentType) {
        return contentType != null && contentType.category() == ContentCategory.PDF;
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        DetectedContentType type = request.detectedContentType() != null
                ? request.detectedContentType()
                : new DetectedContentType(PDF_MIME_TYPE, ContentCategory.PDF, request.filenameHint());
        if (!supports(type)) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "UNSUPPORTED_CONTENT_TYPE: " + type.mimeType() + " is not PDF");
        }

        byte[] content = request.content();
        if (content.length > maxContentBytes) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "CONTENT_TOO_LARGE: " + content.length + " bytes exceed the limit of " + maxContentBytes);
        }

        PDDocument pdf = null;
        try {
            pdf = loader.load(content);
            return extract(request, type, pdf);
        } catch (InvalidPasswordException e) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "PDF_ENCRYPTED: a user password is required to open the document");
        } catch (IOException | RuntimeException e) {
            return ExtractionResult.failure(request.resourceRef(), type, "PDF_PARSE_FAILED: " + describe(e));
        } finally {
            closeQuietly(pdf);
        }
    }

    private ExtractionResult extract(ExtractionRequest request, DetectedContentType type, PDDocument pdf)
            throws IOException {
        List<String> warnings = new ArrayList<String>();
        if (pdf.isEncrypted()) {
            if (!pdf.getCurrentAccessPermission().canExtractContent()) {
                return ExtractionResult.failure(request.resourceRef(), type,
                        "PDF_EXTRACTION_NOT_PERMITTED: the document forbids text extraction");
            }
            warnings.add("PDF_OWNER_PASSWORD_PROTECTED: the document opened without a password");
        }

        ExtractedDocument.Builder document = ExtractedDocument.builder().contentType(type);
        BlockSequence blocks = new BlockSequence(document);
        PDDocumentInformation info = pdf.getDocumentInformation();
        String title = trimToNull(info.getTitle());
        if (title != null) {
            document.title(title);
        }
        blocks.metadata("author", info.getAuthor());
        blocks.metadata("subject", info.getSubject());
        blocks.metadata("keywords", info.getKeywords());
        blocks.metadata("creator", info.getCreator());
        blocks.metadata("producer", info.getProducer());
        blocks.metadata("created", isoInstant(info.getCreationDate()));
        blocks.metadata("modified", isoInstant(info.getModificationDate()));
        int pages = pdf.getNumberOfPages();
        blocks.metadata("pages", String.valueOf(pages));

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");
        int pagesWithText = 0;
        for (int page = 1; page <= pages; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String text = normalizePage(stripper.getText(pdf));
            if (!text.isEmpty()) {
                blocks.text(text, Collections.singletonMap("page", String.valueOf(page)));
                pagesWithText++;
            }
        }
        if (pagesWithText == 0) {
            warnings.add("NO_TEXT_LAYER: no page contains extractable text; OCR is not performed");
        }
        return ExtractionResult.successWithWarnings(request.resourceRef(), type, document.build(), warnings);
    }

    /** Normalizes line endings, trims trailing spaces per line and drops surrounding blank lines. */
    private static String normalizePage(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder result = new StringBuilder(text.length());
        for (String line : lines) {
            int end = line.length();
            while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
                end--;
            }
            result.append(line, 0, end).append('\n');
        }
        return result.toString().trim();
    }

    private static String isoInstant(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(calendar.getTime());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static void closeQuietly(PDDocument pdf) {
        if (pdf == null) {
            return;
        }
        try {
            pdf.close();
        } catch (IOException ignored) {
            // Closing releases scratch memory only; the extraction result is already decided.
        }
    }

    /** Appends blocks with consecutive indexes. */
    private static final class BlockSequence {

        private final ExtractedDocument.Builder document;
        private int nextIndex;

        BlockSequence(ExtractedDocument.Builder document) {
            this.document = document;
        }

        void metadata(String name, String value) {
            String trimmed = trimToNull(value);
            if (trimmed == null) {
                return;
            }
            Map<String, String> attributes = new LinkedHashMap<String, String>();
            attributes.put("name", name);
            attributes.put("value", trimmed);
            document.addBlock(new ExtractedBlock(nextIndex++, BlockKind.METADATA, null, attributes));
        }

        void text(String text, Map<String, String> attributes) {
            document.addBlock(new ExtractedBlock(nextIndex++, BlockKind.TEXT, text, attributes));
        }
    }
}
