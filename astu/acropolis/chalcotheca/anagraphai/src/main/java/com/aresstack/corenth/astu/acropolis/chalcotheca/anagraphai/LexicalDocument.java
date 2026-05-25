package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A document prepared for lexical indexing.
 *
 * <p>A lexical document ties text content (as one or more chunks) to a
 * {@link VirtualResourceRef} — the canonical Corenth resource identity.
 * Optional metadata fields (title, content type) are carried for field-level indexing.
 *
 * <p>Adapted from MainframeMate's indexing pipeline model (Chunk + document ID),
 * with Corenth resource identity replacing raw file paths.
 */
public final class LexicalDocument {

    private final VirtualResourceRef resourceRef;
    private final String title;
    private final String contentType;
    private final List<LexicalChunk> chunks;

    private LexicalDocument(Builder builder) {
        this.resourceRef = builder.resourceRef;
        this.title = builder.title;
        this.contentType = builder.contentType;
        this.chunks = Collections.unmodifiableList(new ArrayList<LexicalChunk>(builder.chunks));
    }

    /** Returns the virtual resource reference that this document belongs to. */
    public VirtualResourceRef resourceRef() {
        return resourceRef;
    }

    /** Returns the document title, or {@code null} if not set. */
    public String title() {
        return title;
    }

    /** Returns the content type, or {@code null} if not set. */
    public String contentType() {
        return contentType;
    }

    /** Returns the text chunks that compose this document. */
    public List<LexicalChunk> chunks() {
        return chunks;
    }

    public static Builder builder(VirtualResourceRef resourceRef) {
        return new Builder(resourceRef);
    }

    public static final class Builder {
        private final VirtualResourceRef resourceRef;
        private String title;
        private String contentType;
        private final List<LexicalChunk> chunks = new ArrayList<LexicalChunk>();

        private Builder(VirtualResourceRef resourceRef) {
            if (resourceRef == null) {
                throw new IllegalArgumentException("resourceRef must not be null");
            }
            this.resourceRef = resourceRef;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder addChunk(LexicalChunk chunk) {
            if (chunk == null) {
                throw new IllegalArgumentException("chunk must not be null");
            }
            this.chunks.add(chunk);
            return this;
        }

        /**
         * Convenience method to add text as a single chunk at index 0.
         *
         * @param text the full document text
         * @return this builder
         */
        public Builder fullText(String text) {
            this.chunks.clear();
            this.chunks.add(new LexicalChunk(0, text));
            return this;
        }

        public LexicalDocument build() {
            if (chunks.isEmpty()) {
                throw new IllegalStateException("Document must have at least one chunk");
            }
            return new LexicalDocument(this);
        }
    }
}
