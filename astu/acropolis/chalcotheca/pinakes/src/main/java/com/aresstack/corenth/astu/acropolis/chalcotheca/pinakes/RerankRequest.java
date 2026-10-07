package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ask a reranker to score candidate passages against a query.
 *
 * <p>Candidates are unique by {@link ChunkKey}. The request carries text only; rerankers
 * read raw passages and never need embedding vectors.
 */
public final class RerankRequest {

    private final String query;
    private final List<RerankCandidate> candidates;

    public RerankRequest(String query, List<RerankCandidate> candidates) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be null or empty");
        }
        Set<ChunkKey> seen = new HashSet<ChunkKey>();
        for (RerankCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidate must not be null");
            }
            if (!seen.add(candidate.key())) {
                throw new IllegalArgumentException("Duplicate candidate " + candidate.key());
            }
        }
        this.query = query;
        this.candidates = Collections.unmodifiableList(new ArrayList<RerankCandidate>(candidates));
    }

    /** Return the query text. */
    public String query() {
        return query;
    }

    /** Return the candidates in their pre-rerank order. */
    public List<RerankCandidate> candidates() {
        return candidates;
    }
}
