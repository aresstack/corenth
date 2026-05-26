package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * A raw resource fetched by a connector.
 *
 * <p>Combines the resource reference with raw content and basic metadata
 * such as filename and last-modified time.
 */
public final class RawResource {

    private final VirtualResourceRef ref;
    private final RawResourceContent content;
    private final String filename;
    private final long lastModifiedMillis;

    public RawResource(VirtualResourceRef ref, RawResourceContent content,
                       String filename, long lastModifiedMillis) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        this.ref = ref;
        this.content = content;
        this.filename = filename;
        this.lastModifiedMillis = lastModifiedMillis;
    }

    /** Returns the virtual resource reference. */
    public VirtualResourceRef ref() {
        return ref;
    }

    /** Returns the raw content. */
    public RawResourceContent content() {
        return content;
    }

    /** Returns the filename extracted from the URI path, or {@code null}. */
    public String filename() {
        return filename;
    }

    /** Returns last modified time in millis since epoch, or 0 if unknown. */
    public long lastModifiedMillis() {
        return lastModifiedMillis;
    }
}
