package com.aresstack.corenth.proasteion.emporion.deigma;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * A request to extract content from a resource.
 *
 * <p>Carries the resource reference from {@code astu}, the raw content,
 * and optional hints to aid content detection and extraction.
 */
public final class ExtractionRequest {

    private final VirtualResourceRef resourceRef;
    private final byte[] content;
    private final String filenameHint;
    private final String contentTypeHint;

    public ExtractionRequest(VirtualResourceRef resourceRef, byte[] content,
                             String filenameHint, String contentTypeHint) {
        if (resourceRef == null) {
            throw new IllegalArgumentException("Resource reference must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content must not be null");
        }
        this.resourceRef = resourceRef;
        this.content = content;
        this.filenameHint = filenameHint;
        this.contentTypeHint = contentTypeHint;
    }

    /** Returns the astu resource reference. */
    public VirtualResourceRef resourceRef() {
        return resourceRef;
    }

    /** Returns the raw content bytes. */
    public byte[] content() {
        return content;
    }

    /** Returns the optional filename hint, or {@code null}. */
    public String filenameHint() {
        return filenameHint;
    }

    /** Returns the optional content type hint, or {@code null}. */
    public String contentTypeHint() {
        return contentTypeHint;
    }
}
