package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Configure one hybrid retrieval: candidate pool sizes and the two optional stages.
 *
 * <p>Semantic widening and reranking are switched independently. With both off, retrieval
 * is plain lexical search. A plan only states what is wanted; whether an optional stage
 * can actually run depends on the adapters wired into {@link HybridRetrieval}.
 */
public final class HybridRetrievalPlan {

    public static final int DEFAULT_LEXICAL_CANDIDATES = 50;
    public static final int DEFAULT_SEMANTIC_CANDIDATES = 50;
    public static final int DEFAULT_RERANK_CANDIDATES = 25;

    private final int finalLimit;
    private final int lexicalCandidates;
    private final boolean semanticEnabled;
    private final int semanticCandidates;
    private final boolean rerankEnabled;
    private final int rerankCandidates;
    private final int fusionRankConstant;

    private HybridRetrievalPlan(Builder builder) {
        this.finalLimit = builder.finalLimit;
        this.lexicalCandidates = builder.lexicalCandidates;
        this.semanticEnabled = builder.semanticEnabled;
        this.semanticCandidates = builder.semanticCandidates;
        this.rerankEnabled = builder.rerankEnabled;
        this.rerankCandidates = builder.rerankCandidates;
        this.fusionRankConstant = builder.fusionRankConstant;
    }

    /** Start a plan that returns at most {@code finalLimit} hits, with both optional stages off. */
    public static Builder builder(int finalLimit) {
        return new Builder(finalLimit);
    }

    /** Create a plain lexical plan. */
    public static HybridRetrievalPlan lexicalOnly(int finalLimit) {
        return builder(finalLimit).build();
    }

    /** Return the maximum number of hits. */
    public int finalLimit() {
        return finalLimit;
    }

    /** Return how many lexical candidates to fetch. */
    public int lexicalCandidates() {
        return lexicalCandidates;
    }

    /** Return whether semantic widening is wanted. */
    public boolean semanticEnabled() {
        return semanticEnabled;
    }

    /** Return how many semantic candidates to fetch. */
    public int semanticCandidates() {
        return semanticCandidates;
    }

    /** Return whether reranking is wanted. */
    public boolean rerankEnabled() {
        return rerankEnabled;
    }

    /** Return how many top candidates to hand to the reranker; never fewer than the final limit. */
    public int rerankCandidates() {
        return Math.max(rerankCandidates, finalLimit);
    }

    /** Return the rank constant used for reciprocal rank fusion. */
    public int fusionRankConstant() {
        return fusionRankConstant;
    }

    @Override
    public String toString() {
        return "HybridRetrievalPlan{finalLimit=" + finalLimit + ", lexical=" + lexicalCandidates
                + ", semantic=" + (semanticEnabled ? String.valueOf(semanticCandidates) : "off")
                + ", rerank=" + (rerankEnabled ? String.valueOf(rerankCandidates()) : "off") + "}";
    }

    public static final class Builder {

        private final int finalLimit;
        private int lexicalCandidates;
        private boolean semanticEnabled;
        private int semanticCandidates = DEFAULT_SEMANTIC_CANDIDATES;
        private boolean rerankEnabled;
        private int rerankCandidates = DEFAULT_RERANK_CANDIDATES;
        private int fusionRankConstant = ReciprocalRankFusion.DEFAULT_RANK_CONSTANT;

        private Builder(int finalLimit) {
            requirePositive(finalLimit, "finalLimit");
            this.finalLimit = finalLimit;
            this.lexicalCandidates = Math.max(DEFAULT_LEXICAL_CANDIDATES, finalLimit);
        }

        public Builder lexicalCandidates(int count) {
            requirePositive(count, "lexicalCandidates");
            this.lexicalCandidates = count;
            return this;
        }

        public Builder semantic(boolean enabled) {
            this.semanticEnabled = enabled;
            return this;
        }

        public Builder semanticCandidates(int count) {
            requirePositive(count, "semanticCandidates");
            this.semanticCandidates = count;
            return this;
        }

        public Builder rerank(boolean enabled) {
            this.rerankEnabled = enabled;
            return this;
        }

        public Builder rerankCandidates(int count) {
            requirePositive(count, "rerankCandidates");
            this.rerankCandidates = count;
            return this;
        }

        public Builder fusionRankConstant(int rankConstant) {
            requirePositive(rankConstant, "fusionRankConstant");
            this.fusionRankConstant = rankConstant;
            return this;
        }

        public HybridRetrievalPlan build() {
            return new HybridRetrievalPlan(this);
        }

        private static void requirePositive(int value, String name) {
            if (value < 1) {
                throw new IllegalArgumentException(name + " must be at least 1");
            }
        }
    }
}
