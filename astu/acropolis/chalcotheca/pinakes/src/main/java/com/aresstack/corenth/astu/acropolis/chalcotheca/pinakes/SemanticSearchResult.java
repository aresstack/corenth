package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Report one semantic hit with its cosine similarity.
 */
public final class SemanticSearchResult {

    private final ChunkKey key;
    private final double similarity;
    private final String text;

    public SemanticSearchResult(ChunkKey key, double similarity, String text) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (Double.isNaN(similarity) || Double.isInfinite(similarity)) {
            throw new IllegalArgumentException("similarity must be finite");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.key = key;
        this.similarity = similarity;
        this.text = text;
    }

    /** Return the chunk identity. */
    public ChunkKey key() {
        return key;
    }

    /** Return the cosine similarity in {@code [-1, 1]}. */
    public double similarity() {
        return similarity;
    }

    /** Return the stored chunk text. */
    public String text() {
        return text;
    }

    @Override
    public String toString() {
        return "SemanticSearchResult{" + key + ", similarity=" + similarity + "}";
    }
}
