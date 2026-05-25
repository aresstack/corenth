package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.Arrays;

/**
 * An immutable request to extract content from a resource.
 *
 * <p>Carries the resource reference from {@code astu}, the raw content,
 * and optional hints to aid content detection and extraction.
 *
 * <p>The byte array is defensively copied on construction and on access
 * to guarantee immutability.
 */
public final class ExtractionRequest {

    private final VirtualResourceRef resourceRef;
    private final byte[] content;
    private final String filenameHint;
    private final String contentTypeHint;
    private final DetectedContentType detectedContentType;

    public ExtractionRequest(VirtualResourceRef resourceRef, byte[] content,
                             String filenameHint, String contentTypeHint) {
        this(resourceRef, content, filenameHint, contentTypeHint, null);
    }

    public ExtractionRequest(VirtualResourceRef resourceRef, byte[] content,
                             String filenameHint, String contentTypeHint,
                             DetectedContentType detectedContentType) {
        if (resourceRef == null) {
            throw new IllegalArgumentException("Resource reference must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content must not be null");
        }
        this.resourceRef = resourceRef;
        this.content = Arrays.copyOf(content, content.length);
        this.filenameHint = filenameHint;
        this.contentTypeHint = contentTypeHint;
        this.detectedContentType = detectedContentType;
    }

    /** Returns the astu resource reference. */
    public VirtualResourceRef resourceRef() {
        return resourceRef;
    }

    /** Returns a defensive copy of the raw content bytes. */
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    /** Returns the optional filename hint, or {@code null}. */
    public String filenameHint() {
        return filenameHint;
    }

    /** Returns the optional content type hint, or {@code null}. */
    public String contentTypeHint() {
        return contentTypeHint;
    }

    /**
     * Returns the detected content type passed from the detection phase,
     * or {@code null} if not provided. Extractors should prefer this over
     * creating their own hardcoded type when present.
     */
    public DetectedContentType detectedContentType() {
        return detectedContentType;
    }
}
