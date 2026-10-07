package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Describe one chunk to place into a semantic index.
 *
 * <p>The entry carries the chunk text so that semantic-only candidates can still be shown
 * and reranked. The text is the same chunk text Anagraphai indexes; Pinakes does not
 * derive or re-chunk it.
 */
public final class SemanticEntry {

    private final ChunkKey key;
    private final EmbeddingVector vector;
    private final String text;

    public SemanticEntry(ChunkKey key, EmbeddingVector vector, String text) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.key = key;
        this.vector = vector;
        this.text = text;
    }

    /** Return the chunk identity. */
    public ChunkKey key() {
        return key;
    }

    /** Return the passage vector of the chunk. */
    public EmbeddingVector vector() {
        return vector;
    }

    /** Return the chunk text. */
    public String text() {
        return text;
    }

    @Override
    public String toString() {
        return "SemanticEntry{" + key + ", " + vector + "}";
    }
}
