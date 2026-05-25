package com.aresstack.corenth.astu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata associated with a virtual resource.
 *
 * <p>Metadata describes properties discovered during scanning or ingestion,
 * such as title, size, last-modified time and content type.
 * It is immutable once constructed.
 */
public final class VirtualResourceMetadata {

    private final String title;
    private final String contentType;
    private final long sizeBytes;
    private final Map<String, String> attributes;

    private VirtualResourceMetadata(Builder builder) {
        this.title = builder.title;
        this.contentType = builder.contentType;
        this.sizeBytes = builder.sizeBytes;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.attributes));
    }

    /** Returns the human-readable title, or {@code null} if unknown. */
    public String title() {
        return title;
    }

    /** Returns the MIME content type, or {@code null} if unknown. */
    public String contentType() {
        return contentType;
    }

    /** Returns the size in bytes, or {@code -1} if unknown. */
    public long sizeBytes() {
        return sizeBytes;
    }

    /** Returns additional attributes as an unmodifiable map. */
    public Map<String, String> attributes() {
        return attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String title;
        private String contentType;
        private long sizeBytes = -1;
        private final Map<String, String> attributes = new LinkedHashMap<String, String>();

        private Builder() {}

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder sizeBytes(long sizeBytes) {
            this.sizeBytes = sizeBytes;
            return this;
        }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public VirtualResourceMetadata build() {
            return new VirtualResourceMetadata(this);
        }
    }
}
