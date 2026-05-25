package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai;

/**
 * A text chunk within a lexical document.
 *
 * <p>A chunk represents a contiguous portion of document content that can be
 * independently indexed and returned as a search result excerpt. Chunks are
 * identified by a zero-based index within their parent document.
 *
 * <p>Adapted from MainframeMate's {@code Chunk} model, but without coupling
 * to RAG-specific embedding or context-window concepts.
 */
public final class LexicalChunk {

    private final int index;
    private final String text;

    /**
     * Creates a chunk with the given index and text content.
     *
     * @param index the zero-based position of this chunk within the document
     * @param text  the text content of this chunk
     */
    public LexicalChunk(int index, String text) {
        if (index < 0) {
            throw new IllegalArgumentException("Chunk index must not be negative");
        }
        if (text == null) {
            throw new IllegalArgumentException("Chunk text must not be null");
        }
        this.index = index;
        this.text = text;
    }

    /** Returns the zero-based position of this chunk within the document. */
    public int index() {
        return index;
    }

    /** Returns the text content of this chunk. */
    public String text() {
        return text;
    }

    @Override
    public String toString() {
        return "LexicalChunk{index=" + index + ", length=" + text.length() + "}";
    }
}
