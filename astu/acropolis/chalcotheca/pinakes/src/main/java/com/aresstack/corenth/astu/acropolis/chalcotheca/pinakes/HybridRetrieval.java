package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalQuery;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retrieve chunks lexically first, optionally widen semantically, optionally rerank.
 *
 * <p>The lexical register is mandatory and its failures propagate. Semantic widening and
 * reranking are independent optional stages: each runs only when the plan asks for it and
 * an adapter is wired, and a failing adapter degrades to the previous ranking with a
 * machine-readable {@link StageOutcome} instead of failing the search. Reranking works on
 * purely lexical candidates as well.
 *
 * <p>Instances are immutable; {@link #withSemantic} and {@link #withReranker} return new
 * instances. The class holds no index state of its own.
 */
public final class HybridRetrieval {

    private static final Comparator<RetrievalHit> RERANKED_BEST_FIRST = new Comparator<RetrievalHit>() {
        @Override
        public int compare(RetrievalHit a, RetrievalHit b) {
            int byScore = Double.compare(b.score(), a.score());
            return byScore != 0 ? byScore : a.key().compareTo(b.key());
        }
    };

    private final LexicalIndex lexicalIndex;
    private final SemanticIndex semanticIndex;
    private final EmbeddingClient embeddingClient;
    private final Reranker reranker;

    private HybridRetrieval(LexicalIndex lexicalIndex, SemanticIndex semanticIndex,
                            EmbeddingClient embeddingClient, Reranker reranker) {
        this.lexicalIndex = lexicalIndex;
        this.semanticIndex = semanticIndex;
        this.embeddingClient = embeddingClient;
        this.reranker = reranker;
    }

    /** Create a retrieval over the lexical register only. */
    public static HybridRetrieval lexical(LexicalIndex lexicalIndex) {
        if (lexicalIndex == null) {
            throw new IllegalArgumentException("lexicalIndex must not be null");
        }
        return new HybridRetrieval(lexicalIndex, null, null, null);
    }

    /** Return a copy that can widen candidates through the given semantic index and client. */
    public HybridRetrieval withSemantic(SemanticIndex index, EmbeddingClient client) {
        if (index == null || client == null) {
            throw new IllegalArgumentException("semantic index and embedding client must not be null");
        }
        return new HybridRetrieval(lexicalIndex, index, client, reranker);
    }

    /** Return a copy that can rerank candidates with the given reranker. */
    public HybridRetrieval withReranker(Reranker newReranker) {
        if (newReranker == null) {
            throw new IllegalArgumentException("reranker must not be null");
        }
        return new HybridRetrieval(lexicalIndex, semanticIndex, embeddingClient, newReranker);
    }

    /**
     * Retrieve chunks for the query according to the plan.
     *
     * @throws IOException if the lexical register cannot be read
     */
    public RetrievalResult retrieve(String query, HybridRetrievalPlan plan) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        List<LexicalSearchResult> lexicalResults =
                lexicalIndex.search(new LexicalQuery(query, plan.lexicalCandidates()));
        Map<ChunkKey, LexicalSearchResult> lexicalByKey = new LinkedHashMap<ChunkKey, LexicalSearchResult>();
        for (LexicalSearchResult result : lexicalResults) {
            ChunkKey key = ChunkKey.of(result);
            if (!lexicalByKey.containsKey(key)) {
                lexicalByKey.put(key, result);
            }
        }

        SemanticStage semantic = runSemanticStage(query, plan);
        List<RetrievalHit> ranked = semantic.outcome == StageOutcome.APPLIED
                ? fuse(lexicalByKey, semantic.results, plan)
                : lexicalRanking(lexicalByKey);

        StageOutcome rerankOutcome;
        String rerankFailure = null;
        if (!plan.rerankEnabled()) {
            rerankOutcome = StageOutcome.DISABLED;
        } else if (reranker == null) {
            rerankOutcome = StageOutcome.NOT_CONFIGURED;
        } else if (ranked.isEmpty()) {
            rerankOutcome = StageOutcome.NO_CANDIDATES;
        } else {
            try {
                ranked = rerank(query, ranked, plan);
                rerankOutcome = StageOutcome.APPLIED;
            } catch (RerankException e) {
                rerankOutcome = StageOutcome.FAILED;
                rerankFailure = describe(e);
            } catch (RuntimeException e) {
                rerankOutcome = StageOutcome.FAILED;
                rerankFailure = describe(e);
            }
        }

        if (ranked.size() > plan.finalLimit()) {
            ranked = ranked.subList(0, plan.finalLimit());
        }
        return new RetrievalResult(ranked, semantic.outcome, semantic.failure, rerankOutcome, rerankFailure);
    }

    private SemanticStage runSemanticStage(String query, HybridRetrievalPlan plan) {
        if (!plan.semanticEnabled()) {
            return new SemanticStage(StageOutcome.DISABLED, null, null);
        }
        if (semanticIndex == null) {
            return new SemanticStage(StageOutcome.NOT_CONFIGURED, null, null);
        }
        if (!embeddingClient.model().equals(semanticIndex.model())) {
            return new SemanticStage(StageOutcome.MODEL_MISMATCH,
                    "client " + embeddingClient.model() + " vs index " + semanticIndex.model(), null);
        }
        try {
            List<EmbeddingVector> vectors =
                    embeddingClient.embed(EmbeddingPurpose.QUERY, Collections.singletonList(query));
            if (vectors == null || vectors.size() != 1 || vectors.get(0) == null) {
                return new SemanticStage(StageOutcome.FAILED, "embedding client must return exactly one vector", null);
            }
            List<SemanticSearchResult> results = semanticIndex.search(vectors.get(0), plan.semanticCandidates());
            if (results.isEmpty()) {
                return new SemanticStage(StageOutcome.NO_CANDIDATES, null, null);
            }
            return new SemanticStage(StageOutcome.APPLIED, null, results);
        } catch (EmbeddingException e) {
            return new SemanticStage(StageOutcome.FAILED, describe(e), null);
        } catch (RuntimeException e) {
            return new SemanticStage(StageOutcome.FAILED, describe(e), null);
        }
    }

    private static List<RetrievalHit> lexicalRanking(Map<ChunkKey, LexicalSearchResult> lexicalByKey) {
        List<RetrievalHit> hits = new ArrayList<RetrievalHit>(lexicalByKey.size());
        int rank = 0;
        for (Map.Entry<ChunkKey, LexicalSearchResult> entry : lexicalByKey.entrySet()) {
            LexicalSearchResult result = entry.getValue();
            rank++;
            hits.add(new RetrievalHit(entry.getKey(), result.excerpt(), result.title(), result.contentType(),
                    result.score(), ScoreSource.LEXICAL, rank, 0));
        }
        return hits;
    }

    private static List<RetrievalHit> fuse(Map<ChunkKey, LexicalSearchResult> lexicalByKey,
                                           List<SemanticSearchResult> semanticResults,
                                           HybridRetrievalPlan plan) {
        Map<ChunkKey, SemanticSearchResult> semanticByKey = new HashMap<ChunkKey, SemanticSearchResult>();
        List<ChunkKey> semanticKeys = new ArrayList<ChunkKey>(semanticResults.size());
        for (SemanticSearchResult result : semanticResults) {
            semanticKeys.add(result.key());
            if (!semanticByKey.containsKey(result.key())) {
                semanticByKey.put(result.key(), result);
            }
        }
        List<ReciprocalRankFusion.FusedRank> fused = new ReciprocalRankFusion(plan.fusionRankConstant())
                .fuse(new ArrayList<ChunkKey>(lexicalByKey.keySet()), semanticKeys);

        List<RetrievalHit> hits = new ArrayList<RetrievalHit>(fused.size());
        for (ReciprocalRankFusion.FusedRank rank : fused) {
            LexicalSearchResult lexical = lexicalByKey.get(rank.key());
            String text = lexical != null ? lexical.excerpt() : semanticByKey.get(rank.key()).text();
            String title = lexical != null ? lexical.title() : null;
            String contentType = lexical != null ? lexical.contentType() : null;
            hits.add(new RetrievalHit(rank.key(), text, title, contentType, rank.score(), ScoreSource.FUSED,
                    rank.lexicalRank(), rank.semanticRank()));
        }
        return hits;
    }

    private List<RetrievalHit> rerank(String query, List<RetrievalHit> ranked, HybridRetrievalPlan plan)
            throws RerankException {
        List<RetrievalHit> pool = ranked.size() > plan.rerankCandidates()
                ? ranked.subList(0, plan.rerankCandidates())
                : ranked;
        Map<ChunkKey, RetrievalHit> poolByKey = new HashMap<ChunkKey, RetrievalHit>();
        List<RerankCandidate> candidates = new ArrayList<RerankCandidate>(pool.size());
        for (RetrievalHit hit : pool) {
            poolByKey.put(hit.key(), hit);
            candidates.add(new RerankCandidate(hit.key(), hit.text()));
        }

        List<RerankedResult> scores = reranker.rerank(new RerankRequest(query, candidates));
        if (scores == null) {
            throw new RerankException("reranker returned no result list");
        }
        Set<ChunkKey> seen = new HashSet<ChunkKey>();
        List<RetrievalHit> reranked = new ArrayList<RetrievalHit>(scores.size());
        for (RerankedResult score : scores) {
            if (score == null) {
                throw new RerankException("reranker returned a null result");
            }
            RetrievalHit hit = poolByKey.get(score.key());
            if (hit == null) {
                throw new RerankException("reranker scored unknown candidate " + score.key());
            }
            if (!seen.add(score.key())) {
                throw new RerankException("reranker scored candidate twice: " + score.key());
            }
            reranked.add(new RetrievalHit(hit.key(), hit.text(), hit.title(), hit.contentType(), score.score(),
                    ScoreSource.RERANKED, hit.lexicalRank(), hit.semanticRank()));
        }
        Collections.sort(reranked, RERANKED_BEST_FIRST);
        return reranked;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + message;
    }

    private static final class SemanticStage {

        private final StageOutcome outcome;
        private final String failure;
        private final List<SemanticSearchResult> results;

        private SemanticStage(StageOutcome outcome, String failure, List<SemanticSearchResult> results) {
            this.outcome = outcome;
            this.failure = failure;
            this.results = results;
        }
    }
}
