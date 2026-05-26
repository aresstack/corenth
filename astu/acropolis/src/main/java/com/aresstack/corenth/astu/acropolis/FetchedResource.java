package com.aresstack.corenth.astu.acropolis;

import java.util.Arrays;

/**
 * Immutable DTO carrying raw content fetched from a resource provider.
 */
public final class FetchedResource {

    private final byte[] bytes;
    private final String filename;
    private final long sizeBytes;

    public FetchedResource(byte[] bytes, String filename, long sizeBytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.filename = filename;
        this.sizeBytes = sizeBytes;
    }

    /** Returns a defensive copy of the raw content bytes. */
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /** Returns the filename, or {@code null} if unknown. */
    public String filename() {
        return filename;
    }

    /** Returns the content size in bytes. */
    public long sizeBytes() {
        return sizeBytes;
    }
}
