package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Hand one candidate passage to a reranker.
 */
public final class RerankCandidate {

    private final ChunkKey key;
    private final String text;

    public RerankCandidate(ChunkKey key, String text) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.key = key;
        this.text = text;
    }

    /** Return the chunk identity. */
    public ChunkKey key() {
        return key;
    }

    /** Return the raw passage text the reranker reads. */
    public String text() {
        return text;
    }
}
