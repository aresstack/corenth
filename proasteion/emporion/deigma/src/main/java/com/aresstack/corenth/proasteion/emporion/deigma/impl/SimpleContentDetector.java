package com.aresstack.corenth.proasteion.emporion.deigma.impl;

import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentDetector;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A simple content detector based on filename extensions and MIME type hints.
 *
 * <p>Does not require external dependencies (no Tika). Uses a built-in
 * mapping of common extensions and MIME types to content categories, and
 * sniffs a small set of unambiguous content signatures (PDF magic bytes,
 * leading HTML markup) when the hints are missing or inconclusive.
 */
public final class SimpleContentDetector implements ContentDetector {

    private static final Map<String, MimeCategory> EXTENSION_MAP = new HashMap<String, MimeCategory>();
    private static final Map<String, ContentCategory> MIME_CATEGORY_MAP = new HashMap<String, ContentCategory>();
    private static final Set<String> SOURCE_EXTENSIONS = new HashSet<String>();
    private static final String[] HTML_SIGNATURES = {"<!doctype html", "<html", "<head", "<body"};
    private static final int HTML_SNIFF_LIMIT = 1024;

    static {
        // Plain text
        EXTENSION_MAP.put("txt", new MimeCategory("text/plain", ContentCategory.PLAIN_TEXT));
        EXTENSION_MAP.put("log", new MimeCategory("text/plain", ContentCategory.PLAIN_TEXT));
        EXTENSION_MAP.put("cfg", new MimeCategory("text/plain", ContentCategory.PLAIN_TEXT));
        EXTENSION_MAP.put("ini", new MimeCategory("text/plain", ContentCategory.PLAIN_TEXT));
        EXTENSION_MAP.put("properties", new MimeCategory("text/plain", ContentCategory.PLAIN_TEXT));

        // Markdown
        EXTENSION_MAP.put("md", new MimeCategory("text/markdown", ContentCategory.MARKDOWN));
        EXTENSION_MAP.put("markdown", new MimeCategory("text/markdown", ContentCategory.MARKDOWN));

        // HTML
        EXTENSION_MAP.put("html", new MimeCategory("text/html", ContentCategory.HTML));
        EXTENSION_MAP.put("htm", new MimeCategory("text/html", ContentCategory.HTML));
        EXTENSION_MAP.put("xhtml", new MimeCategory("application/xhtml+xml", ContentCategory.HTML));

        // PDF
        EXTENSION_MAP.put("pdf", new MimeCategory("application/pdf", ContentCategory.PDF));

        // Office documents
        EXTENSION_MAP.put("docx", new MimeCategory("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ContentCategory.OFFICE_DOCUMENT));
        EXTENSION_MAP.put("doc", new MimeCategory("application/msword", ContentCategory.OFFICE_DOCUMENT));
        EXTENSION_MAP.put("xlsx", new MimeCategory("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ContentCategory.OFFICE_DOCUMENT));
        EXTENSION_MAP.put("xls", new MimeCategory("application/vnd.ms-excel", ContentCategory.OFFICE_DOCUMENT));
        EXTENSION_MAP.put("pptx", new MimeCategory("application/vnd.openxmlformats-officedocument.presentationml.presentation", ContentCategory.OFFICE_DOCUMENT));
        EXTENSION_MAP.put("odt", new MimeCategory("application/vnd.oasis.opendocument.text", ContentCategory.OFFICE_DOCUMENT));
        EXTENSION_MAP.put("ods", new MimeCategory("application/vnd.oasis.opendocument.spreadsheet", ContentCategory.OFFICE_DOCUMENT));

        // Structured data
        EXTENSION_MAP.put("json", new MimeCategory("application/json", ContentCategory.STRUCTURED_DATA));
        EXTENSION_MAP.put("xml", new MimeCategory("application/xml", ContentCategory.STRUCTURED_DATA));
        EXTENSION_MAP.put("csv", new MimeCategory("text/csv", ContentCategory.STRUCTURED_DATA));
        EXTENSION_MAP.put("yaml", new MimeCategory("application/x-yaml", ContentCategory.STRUCTURED_DATA));
        EXTENSION_MAP.put("yml", new MimeCategory("application/x-yaml", ContentCategory.STRUCTURED_DATA));

        // Source code extensions
        SOURCE_EXTENSIONS.add("java");
        SOURCE_EXTENSIONS.add("kt");
        SOURCE_EXTENSIONS.add("scala");
        SOURCE_EXTENSIONS.add("py");
        SOURCE_EXTENSIONS.add("js");
        SOURCE_EXTENSIONS.add("ts");
        SOURCE_EXTENSIONS.add("c");
        SOURCE_EXTENSIONS.add("h");
        SOURCE_EXTENSIONS.add("cpp");
        SOURCE_EXTENSIONS.add("hpp");
        SOURCE_EXTENSIONS.add("cs");
        SOURCE_EXTENSIONS.add("go");
        SOURCE_EXTENSIONS.add("rs");
        SOURCE_EXTENSIONS.add("rb");
        SOURCE_EXTENSIONS.add("php");
        SOURCE_EXTENSIONS.add("swift");
        SOURCE_EXTENSIONS.add("cbl");
        SOURCE_EXTENSIONS.add("cob");
        SOURCE_EXTENSIONS.add("nat");
        SOURCE_EXTENSIONS.add("nsp");
        SOURCE_EXTENSIONS.add("jcl");
        SOURCE_EXTENSIONS.add("sh");
        SOURCE_EXTENSIONS.add("bat");
        SOURCE_EXTENSIONS.add("ps1");
        SOURCE_EXTENSIONS.add("sql");
        SOURCE_EXTENSIONS.add("groovy");
        SOURCE_EXTENSIONS.add("gradle");

        // MIME type to category mapping
        MIME_CATEGORY_MAP.put("text/plain", ContentCategory.PLAIN_TEXT);
        MIME_CATEGORY_MAP.put("text/markdown", ContentCategory.MARKDOWN);
        MIME_CATEGORY_MAP.put("text/html", ContentCategory.HTML);
        MIME_CATEGORY_MAP.put("application/xhtml+xml", ContentCategory.HTML);
        MIME_CATEGORY_MAP.put("application/pdf", ContentCategory.PDF);
        MIME_CATEGORY_MAP.put("application/msword", ContentCategory.OFFICE_DOCUMENT);
        MIME_CATEGORY_MAP.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ContentCategory.OFFICE_DOCUMENT);
        MIME_CATEGORY_MAP.put("application/vnd.ms-excel", ContentCategory.OFFICE_DOCUMENT);
        MIME_CATEGORY_MAP.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ContentCategory.OFFICE_DOCUMENT);
        MIME_CATEGORY_MAP.put("application/json", ContentCategory.STRUCTURED_DATA);
        MIME_CATEGORY_MAP.put("application/xml", ContentCategory.STRUCTURED_DATA);
        MIME_CATEGORY_MAP.put("text/xml", ContentCategory.STRUCTURED_DATA);
        MIME_CATEGORY_MAP.put("text/csv", ContentCategory.STRUCTURED_DATA);
    }

    /**
     * Detects content type using the following precedence:
     * <ol>
     *   <li>Magic bytes for strong signatures (e.g. PDF)</li>
     *   <li>Explicit MIME/content-type hint</li>
     *   <li>Filename extension</li>
     *   <li>Leading HTML markup in the content prefix (only when hints are inconclusive)</li>
     *   <li>Fallback to unknown/octet-stream</li>
     * </ol>
     */
    @Override
    public DetectedContentType detect(String filenameHint, String contentTypeHint, byte[] contentPrefix) {
        // 1. Try magic bytes for strong signatures
        if (contentPrefix != null && contentPrefix.length >= 4) {
            if (contentPrefix[0] == '%' && contentPrefix[1] == 'P'
                    && contentPrefix[2] == 'D' && contentPrefix[3] == 'F') {
                return new DetectedContentType("application/pdf", ContentCategory.PDF, filenameHint);
            }
        }

        // 2. Try explicit MIME/content-type hint
        if (contentTypeHint != null && !contentTypeHint.isEmpty()) {
            String normalized = contentTypeHint.toLowerCase(Locale.ROOT).trim();
            // Strip parameters (e.g. charset)
            int semicolon = normalized.indexOf(';');
            if (semicolon > 0) {
                normalized = normalized.substring(0, semicolon).trim();
            }
            ContentCategory category = MIME_CATEGORY_MAP.get(normalized);
            if (category != null) {
                return new DetectedContentType(normalized, category, filenameHint);
            }
            // If MIME starts with text/ but isn't mapped, treat as plain text
            if (normalized.startsWith("text/")) {
                return new DetectedContentType(normalized, ContentCategory.PLAIN_TEXT, filenameHint);
            }
        }

        // 3. Try filename extension
        if (filenameHint != null && !filenameHint.isEmpty()) {
            String ext = extractExtension(filenameHint);
            if (ext != null) {
                // Check source code
                if (SOURCE_EXTENSIONS.contains(ext)) {
                    return new DetectedContentType("text/x-source-code", ContentCategory.SOURCE_CODE, filenameHint);
                }
                // Check known extension mapping
                MimeCategory mc = EXTENSION_MAP.get(ext);
                if (mc != null) {
                    return new DetectedContentType(mc.mimeType, mc.category, filenameHint);
                }
            }
        }

        // 4. Sniff leading HTML markup; hints that named a type have already won above
        if (looksLikeHtml(contentPrefix)) {
            return new DetectedContentType("text/html", ContentCategory.HTML, filenameHint);
        }

        // 5. Fallback
        return new DetectedContentType("application/octet-stream", ContentCategory.UNKNOWN, filenameHint);
    }

    /**
     * Returns {@code true} if the prefix starts with an HTML document signature, ignoring a
     * UTF-8 byte order mark, leading whitespace, an XML declaration and leading comments.
     */
    private static boolean looksLikeHtml(byte[] contentPrefix) {
        if (contentPrefix == null || contentPrefix.length == 0) {
            return false;
        }
        int offset = 0;
        if (contentPrefix.length >= 3 && (contentPrefix[0] & 0xFF) == 0xEF
                && (contentPrefix[1] & 0xFF) == 0xBB && (contentPrefix[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        int length = Math.min(contentPrefix.length - offset, HTML_SNIFF_LIMIT);
        // ISO-8859-1 maps every byte to one char, so ASCII markup is read safely from any ASCII-compatible encoding
        String head = new String(contentPrefix, offset, length, Charset.forName("ISO-8859-1")).toLowerCase(Locale.ROOT);
        int position = skipWhitespace(head, 0);
        if (head.startsWith("<?xml", position)) {
            int end = head.indexOf("?>", position);
            if (end < 0) {
                return false;
            }
            position = skipWhitespace(head, end + 2);
        }
        while (head.startsWith("<!--", position)) {
            int end = head.indexOf("-->", position + 4);
            if (end < 0) {
                return false;
            }
            position = skipWhitespace(head, end + 3);
        }
        for (String signature : HTML_SIGNATURES) {
            if (head.startsWith(signature, position)) {
                int next = position + signature.length();
                return next == head.length() || isTagNameTerminator(head.charAt(next));
            }
        }
        return false;
    }

    private static int skipWhitespace(String text, int position) {
        int index = position;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isTagNameTerminator(char c) {
        return c == '>' || c == '/' || Character.isWhitespace(c);
    }

    private static String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return null;
        }
        // Handle paths with separators
        int lastSep = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (lastDot < lastSep) {
            return null;
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private static final class MimeCategory {
        final String mimeType;
        final ContentCategory category;

        MimeCategory(String mimeType, ContentCategory category) {
            this.mimeType = mimeType;
            this.category = category;
        }
    }
}
