package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carry the hits of one hybrid retrieval and the outcome of each optional stage.
 */
public final class RetrievalResult {

    private final List<RetrievalHit> hits;
    private final StageOutcome semanticOutcome;
    private final String semanticFailure;
    private final StageOutcome rerankOutcome;
    private final String rerankFailure;

    public RetrievalResult(List<RetrievalHit> hits,
                           StageOutcome semanticOutcome, String semanticFailure,
                           StageOutcome rerankOutcome, String rerankFailure) {
        if (hits == null) {
            throw new IllegalArgumentException("hits must not be null");
        }
        if (semanticOutcome == null || rerankOutcome == null) {
            throw new IllegalArgumentException("stage outcomes must not be null");
        }
        this.hits = Collections.unmodifiableList(new ArrayList<RetrievalHit>(hits));
        this.semanticOutcome = semanticOutcome;
        this.semanticFailure = semanticFailure;
        this.rerankOutcome = rerankOutcome;
        this.rerankFailure = rerankFailure;
    }

    /** Return the hits, best first. */
    public List<RetrievalHit> hits() {
        return hits;
    }

    /** Return what happened to the semantic widening stage. */
    public StageOutcome semanticOutcome() {
        return semanticOutcome;
    }

    /** Return the failure description of the semantic stage, or {@code null}. */
    public String semanticFailure() {
        return semanticFailure;
    }

    /** Return what happened to the reranking stage. */
    public StageOutcome rerankOutcome() {
        return rerankOutcome;
    }

    /** Return the failure description of the reranking stage, or {@code null}. */
    public String rerankFailure() {
        return rerankFailure;
    }
}
