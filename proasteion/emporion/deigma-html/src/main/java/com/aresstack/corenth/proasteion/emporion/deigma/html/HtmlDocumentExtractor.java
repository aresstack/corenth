package com.aresstack.corenth.proasteion.emporion.deigma.html;

import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts visible text and shallow structure from HTML resources.
 *
 * <p>Maps the document title to {@link ExtractedDocument#title()}, document metadata
 * (charset, language, description, keywords, author) to text-less {@code METADATA} blocks,
 * and the visible body content to {@code HEADING}, {@code TEXT}, {@code LIST},
 * {@code TABLE} and {@code CODE} blocks in document order. Scripts, styles and other
 * non-visible markup are dropped; malformed markup is repaired by the parser instead of
 * failing.
 *
 * <p>The character encoding is resolved from a byte order mark, then from the
 * {@code charset} parameter of the content-type hint, then from a {@code <meta>}
 * declaration, and falls back to UTF-8.
 *
 * <p>Failures are results, never exceptions. Failure messages start with a stable code:
 * {@code UNSUPPORTED_CONTENT_TYPE}, {@code CONTENT_TOO_LARGE} or {@code HTML_PARSE_FAILED}.
 *
 * <p>The parser library is an implementation detail of this module; no parser type
 * appears in this class's signatures.
 */
public final class HtmlDocumentExtractor implements ResourceExtractor {

    /** Largest input this extractor accepts; larger inputs yield {@code CONTENT_TOO_LARGE}. */
    public static final long DEFAULT_MAX_CONTENT_BYTES = 64L * 1024 * 1024;

    private static final String HTML_MIME_TYPE = "text/html";

    private final long maxContentBytes;

    public HtmlDocumentExtractor() {
        this(DEFAULT_MAX_CONTENT_BYTES);
    }

    HtmlDocumentExtractor(long maxContentBytes) {
        if (maxContentBytes < 0) {
            throw new IllegalArgumentException("Maximum content size must not be negative");
        }
        this.maxContentBytes = maxContentBytes;
    }

    @Override
    public boolean supports(DetectedContentType contentType) {
        return contentType != null && contentType.category() == ContentCategory.HTML;
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        DetectedContentType type = request.detectedContentType() != null
                ? request.detectedContentType()
                : new DetectedContentType(HTML_MIME_TYPE, ContentCategory.HTML, request.filenameHint());
        if (!supports(type)) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "UNSUPPORTED_CONTENT_TYPE: " + type.mimeType() + " is not HTML");
        }

        byte[] content = request.content();
        if (content.length > maxContentBytes) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "CONTENT_TOO_LARGE: " + content.length + " bytes exceed the limit of " + maxContentBytes);
        }

        List<String> warnings = new ArrayList<String>();
        String charsetName = HtmlCharsetHint.fromContentType(request.contentTypeHint(), warnings);

        Document html;
        try {
            html = Jsoup.parse(new ByteArrayInputStream(content), charsetName, "");
        } catch (IOException e) {
            return ExtractionResult.failure(request.resourceRef(), type, "HTML_PARSE_FAILED: " + e.getMessage());
        } catch (RuntimeException e) {
            return ExtractionResult.failure(request.resourceRef(), type, "HTML_PARSE_FAILED: " + e.getMessage());
        }

        ExtractedDocument.Builder document = ExtractedDocument.builder().contentType(type);
        String title = html.title().trim();
        if (!title.isEmpty()) {
            document.title(title);
        }
        HtmlBlockCollector collector = new HtmlBlockCollector(document);
        collector.collect(html);
        if (collector.visibleBlockCount() == 0) {
            warnings.add("NO_VISIBLE_TEXT: the HTML document contains no visible text");
        }

        return ExtractionResult.successWithWarnings(request.resourceRef(), type, document.build(), warnings);
    }
}
