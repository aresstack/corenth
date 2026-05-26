package com.aresstack.corenth.astu.acropolis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of content inspection (detection + extraction).
 *
 * <p>Contains the MIME type and text blocks extracted from the resource.
 * Only text-bearing blocks are included; metadata-only blocks are excluded.
 */
public final class InspectionResult {

    private final boolean success;
    private final String mimeType;
    private final List<String> textBlocks;
    private final String errorMessage;

    private InspectionResult(boolean success, String mimeType,
                             List<String> textBlocks, String errorMessage) {
        this.success = success;
        this.mimeType = mimeType;
        this.textBlocks = textBlocks != null
                ? Collections.unmodifiableList(new ArrayList<String>(textBlocks))
                : Collections.<String>emptyList();
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful result with extracted text blocks.
     *
     * @param mimeType   the detected MIME type
     * @param textBlocks the extracted text blocks (null/blank entries are filtered)
     */
    public static InspectionResult success(String mimeType, List<String> textBlocks) {
        if (mimeType == null || mimeType.trim().isEmpty()) {
            throw new IllegalArgumentException("mimeType must not be null or empty");
        }
        List<String> filtered = new ArrayList<String>();
        if (textBlocks != null) {
            for (String block : textBlocks) {
                if (block != null && !block.trim().isEmpty()) {
                    filtered.add(block);
                }
            }
        }
        return new InspectionResult(true, mimeType, filtered, null);
    }

    /**
     * Creates a failure result.
     *
     * @param errorMessage the reason for failure
     */
    public static InspectionResult failure(String errorMessage) {
        return new InspectionResult(false, null, null, errorMessage);
    }

    /** Returns {@code true} if inspection succeeded. */
    public boolean isSuccess() {
        return success;
    }

    /** Returns the detected MIME type, or {@code null} on failure. */
    public String mimeType() {
        return mimeType;
    }

    /** Returns the text-bearing blocks (may be empty). */
    public List<String> textBlocks() {
        return textBlocks;
    }

    /** Returns the error message, or {@code null} on success. */
    public String errorMessage() {
        return errorMessage;
    }
}
