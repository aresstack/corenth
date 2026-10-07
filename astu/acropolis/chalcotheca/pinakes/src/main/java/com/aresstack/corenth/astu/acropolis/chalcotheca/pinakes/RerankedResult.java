package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Report the relevance a reranker assigned to one candidate.
 */
public final class RerankedResult {

    private final ChunkKey key;
    private final double score;

    public RerankedResult(ChunkKey key, double score) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        this.key = key;
        this.score = score;
    }

    /** Return the chunk identity of the scored candidate. */
    public ChunkKey key() {
        return key;
    }

    /** Return the reranker relevance score; higher is more relevant. */
    public double score() {
        return score;
    }
}
