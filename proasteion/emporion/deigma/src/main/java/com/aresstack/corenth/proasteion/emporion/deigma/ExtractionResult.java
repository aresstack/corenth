package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of an extraction attempt.
 *
 * <p>Contains either a successfully extracted document or a failure state,
 * along with any warnings generated during extraction.
 */
public final class ExtractionResult {

    private final VirtualResourceRef resourceRef;
    private final DetectedContentType detectedType;
    private final ExtractedDocument document;
    private final String errorMessage;
    private final List<String> warnings;

    private ExtractionResult(VirtualResourceRef resourceRef, DetectedContentType detectedType,
                             ExtractedDocument document, String errorMessage, List<String> warnings) {
        this.resourceRef = resourceRef;
        this.detectedType = detectedType;
        this.document = document;
        this.errorMessage = errorMessage;
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    /** Creates a successful result. */
    public static ExtractionResult success(VirtualResourceRef resourceRef,
                                           DetectedContentType detectedType,
                                           ExtractedDocument document) {
        return new ExtractionResult(resourceRef, detectedType, document, null,
                Collections.<String>emptyList());
    }

    /** Creates a successful result with warnings. */
    public static ExtractionResult successWithWarnings(VirtualResourceRef resourceRef,
                                                       DetectedContentType detectedType,
                                                       ExtractedDocument document,
                                                       List<String> warnings) {
        return new ExtractionResult(resourceRef, detectedType, document, null, warnings);
    }

    /** Creates a failure result. */
    public static ExtractionResult failure(VirtualResourceRef resourceRef,
                                           DetectedContentType detectedType,
                                           String errorMessage) {
        return new ExtractionResult(resourceRef, detectedType, null, errorMessage,
                Collections.<String>emptyList());
    }

    /** Returns the astu resource reference. */
    public VirtualResourceRef resourceRef() {
        return resourceRef;
    }

    /** Returns the detected content type, or {@code null} if detection failed. */
    public DetectedContentType detectedType() {
        return detectedType;
    }

    /** Returns the extracted document, or {@code null} if extraction failed. */
    public ExtractedDocument document() {
        return document;
    }

    /** Returns {@code true} if extraction was successful. */
    public boolean isSuccess() {
        return document != null;
    }

    /** Returns the error message, or {@code null} if successful. */
    public String errorMessage() {
        return errorMessage;
    }

    /** Returns any warnings from extraction (may be empty). */
    public List<String> warnings() {
        return warnings;
    }
}
