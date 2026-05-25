package com.aresstack.corenth.proasteion.emporion.deigma;

/**
 * Broad classification of content for routing decisions.
 *
 * <p>Source-code files are detected here but routed to {@code propylaea}
 * for deep analysis rather than being fully parsed in {@code deigma}.
 */
public enum ContentCategory {

    /** Plain text content. */
    PLAIN_TEXT,

    /** Markdown formatted text. */
    MARKDOWN,

    /** HTML content. */
    HTML,

    /** PDF document. */
    PDF,

    /** Office document (DOCX, XLSX, PPTX, etc.). */
    OFFICE_DOCUMENT,

    /** Source code — should be routed to propylaea for deep analysis. */
    SOURCE_CODE,

    /** Structured data (CSV, JSON, XML). */
    STRUCTURED_DATA,

    /** Binary or unknown content. */
    BINARY,

    /** Content type could not be determined. */
    UNKNOWN
}
