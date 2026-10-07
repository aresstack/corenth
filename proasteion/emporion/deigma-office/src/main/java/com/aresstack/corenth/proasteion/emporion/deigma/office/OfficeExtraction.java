package com.aresstack.corenth.proasteion.emporion.deigma.office;

import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Shared guard, container and metadata handling for the OOXML extractors.
 */
final class OfficeExtraction {

    /** Largest input the Office extractors accept. */
    static final long DEFAULT_MAX_CONTENT_BYTES = 64L * 1024 * 1024;

    private OfficeExtraction() {
    }

    /** Maps the parsed document onto the builder and returns the outcome; may throw on broken content. */
    interface Mapper<T> {
        ExtractionResult map(T parsed, ExtractedDocument.Builder document, List<String> warnings) throws IOException;
    }

    /**
     * Runs the common extraction frame: size guard, legacy/encrypted container check,
     * parsing, mapping and cleanup on every path.
     */
    static <T extends Closeable> ExtractionResult extract(ExtractionRequest request, DetectedContentType type,
                                                         long maxContentBytes, String formatName,
                                                         OfficeDocumentLoader<T> loader, Mapper<T> mapper,
                                                         List<String> warnings) {
        byte[] content = request.content();
        if (content.length > maxContentBytes) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "CONTENT_TOO_LARGE: " + content.length + " bytes exceed the limit of " + maxContentBytes);
        }
        String containerFailure = rejectOle2Container(content, formatName);
        if (containerFailure != null) {
            return ExtractionResult.failure(request.resourceRef(), type, containerFailure);
        }
        T parsed = null;
        try {
            parsed = loader.load(content);
            ExtractedDocument.Builder document = ExtractedDocument.builder().contentType(type);
            return mapper.map(parsed, document, warnings);
        } catch (IOException | RuntimeException e) {
            return ExtractionResult.failure(request.resourceRef(), type,
                    "OFFICE_PARSE_FAILED: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } finally {
            closeQuietly(parsed);
        }
    }

    /**
     * Returns a failure message for OLE2 containers: password-protected OOXML files are stored
     * as an OLE2 container with an {@code EncryptedPackage} stream, legacy binary files are OLE2 too.
     */
    private static String rejectOle2Container(byte[] content, String formatName) {
        try {
            if (FileMagic.valueOf(content) != FileMagic.OLE2) {
                return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
        try {
            InputStream in = new ByteArrayInputStream(content);
            POIFSFileSystem container = new POIFSFileSystem(in);
            try {
                if (container.getRoot().hasEntry("EncryptedPackage")) {
                    return "OFFICE_ENCRYPTED: the " + formatName + " document is password protected";
                }
            } finally {
                container.close();
            }
        } catch (IOException | RuntimeException e) {
            return "OFFICE_PARSE_FAILED: unreadable OLE2 container";
        }
        return "UNSUPPORTED_FORMAT: legacy binary Office document, not " + formatName;
    }

    /** Adds core document properties: title to the document, the rest as metadata blocks. */
    static void addCoreProperties(POIXMLProperties properties, ExtractedDocument.Builder document, BlockSequence blocks) {
        if (properties == null) {
            return;
        }
        POIXMLProperties.CoreProperties core = properties.getCoreProperties();
        String title = trimToNull(core.getTitle());
        if (title != null) {
            document.title(title);
        }
        blocks.metadata("author", core.getCreator());
        blocks.metadata("subject", core.getSubject());
        blocks.metadata("keywords", core.getKeywords());
        blocks.metadata("description", core.getDescription());
        blocks.metadata("created", isoInstant(core.getCreated()));
        blocks.metadata("modified", isoInstant(core.getModified()));
    }

    static String isoInstant(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(date);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Collapses whitespace runs, including line breaks, into single spaces. */
    static String collapseWhitespace(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || Character.isWhitespace(c)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.append(c);
            }
        }
        return result.toString();
    }

    static String join(List<String> parts, char separator) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result.append(separator);
            }
            result.append(parts.get(i));
        }
        return result.toString();
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // The document was read from memory; the extraction result is already decided.
        }
    }

    /** Appends blocks with consecutive indexes and counts the visible ones. */
    static final class BlockSequence {

        private final ExtractedDocument.Builder document;
        private int nextIndex;
        private int visibleBlocks;

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

        void visible(BlockKind kind, String text, Map<String, String> attributes) {
            document.addBlock(new ExtractedBlock(nextIndex++, kind, text, attributes));
            visibleBlocks++;
        }

        int visibleBlocks() {
            return visibleBlocks;
        }
    }
}
