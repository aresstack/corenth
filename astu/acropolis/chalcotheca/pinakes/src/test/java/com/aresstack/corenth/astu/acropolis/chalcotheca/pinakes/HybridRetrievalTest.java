package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrievalTest {

    private final ChunkKey invoiceIntro = TestRefs.chunk("invoice.txt", 0);
    private final ChunkKey invoiceTotals = TestRefs.chunk("invoice.txt", 1);
    private final ChunkKey vehicleNote = TestRefs.chunk("vehicle.txt", 0);

    private ScriptedLexicalIndex lexical;
    private TokenHashEmbeddingClient embeddings;
    private InMemorySemanticIndex semanticIndex;

    @BeforeEach
    void setUp() throws EmbeddingException {
        lexical = new ScriptedLexicalIndex()
                .add(invoiceIntro, 2.5f, "invoice for the car repair")
                .add(invoiceTotals, 1.0f, "totals of the invoice");
        embeddings = new TokenHashEmbeddingClient(64).withSynonyms("car", "vehicle", "automobile");
        semanticIndex = new InMemorySemanticIndex(embeddings.model());
        index(invoiceIntro, "invoice for the car repair");
        index(invoiceTotals, "totals of the invoice");
        index(vehicleNote, "vehicle inspection booked");
    }

    private void index(ChunkKey key, String text) throws EmbeddingException {
        EmbeddingVector vector = embeddings.embed(EmbeddingPurpose.PASSAGE, Collections.singletonList(text)).get(0);
        semanticIndex.upsert(new SemanticEntry(key, vector, text));
    }

    @Test
    void lexicalOnlyKeepsTheLexicalRankingAndNeedsNoSemanticAdapters() throws IOException {
        RetrievalResult result = HybridRetrieval.lexical(lexical).retrieve("car", HybridRetrievalPlan.lexicalOnly(10));

        assertEquals(Arrays.asList(invoiceIntro, invoiceTotals), keys(result));
        RetrievalHit first = result.hits().get(0);
        assertEquals(2.5d, first.score(), 1e-6);
        assertEquals(ScoreSource.LEXICAL, first.scoreSource());
        assertEquals(1, first.lexicalRank());
        assertEquals(0, first.semanticRank());
        assertEquals("invoice for the car repair", first.text());
        assertEquals("text/plain", first.contentType());
        assertEquals(StageOutcome.DISABLED, result.semanticOutcome());
        assertEquals(StageOutcome.DISABLED, result.rerankOutcome());
    }

    @Test
    void disabledSemanticStageNeverCallsTheEmbeddingClient() throws IOException {
        int before = embeddings.calls().size();
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical).withSemantic(semanticIndex, embeddings);

        RetrievalResult result = retrieval.retrieve("car", HybridRetrievalPlan.lexicalOnly(10));

        assertEquals(before, embeddings.calls().size());
        assertEquals(StageOutcome.DISABLED, result.semanticOutcome());
        assertEquals(Arrays.asList(invoiceIntro, invoiceTotals), keys(result));
    }

    @Test
    void requestedButUnwiredStagesReportNotConfigured() throws IOException {
        HybridRetrievalPlan plan = HybridRetrievalPlan.builder(10).semantic(true).rerank(true).build();

        RetrievalResult result = HybridRetrieval.lexical(lexical).retrieve("car", plan);

        assertEquals(StageOutcome.NOT_CONFIGURED, result.semanticOutcome());
        assertEquals(StageOutcome.NOT_CONFIGURED, result.rerankOutcome());
        assertEquals(Arrays.asList(invoiceIntro, invoiceTotals), keys(result));
        assertEquals(ScoreSource.LEXICAL, result.hits().get(0).scoreSource());
    }

    @Test
    void semanticStageWidensCandidatesAndMapsHitsToTheirSources() throws IOException {
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical).withSemantic(semanticIndex, embeddings);
        HybridRetrievalPlan plan = HybridRetrievalPlan.builder(10).semantic(true).build();

        RetrievalResult result = retrieval.retrieve("car", plan);

        assertEquals(StageOutcome.APPLIED, result.semanticOutcome());
        assertNull(result.semanticFailure());
        assertTrue(embeddings.calls().contains("QUERY:car"));
        RetrievalHit top = result.hits().get(0);
        assertEquals(invoiceIntro, top.key());
        assertEquals(ScoreSource.FUSED, top.scoreSource());
        assertEquals(1, top.lexicalRank());
        assertTrue(top.semanticRank() > 0);
        assertEquals("Title file:///invoice.txt", top.title());

        RetrievalHit widened = hit(result, vehicleNote);
        assertNotNull(widened, "the synonym chunk must be found semantically");
        assertEquals(0, widened.lexicalRank());
        assertTrue(widened.semanticRank() > 0);
        assertEquals("vehicle inspection booked", widened.text());
        assertNull(widened.title());
        assertNull(widened.contentType());
    }

    @Test
    void retrievalIsDeterministic() throws IOException {
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical).withSemantic(semanticIndex, embeddings);
        HybridRetrievalPlan plan = HybridRetrievalPlan.builder(10).semantic(true).build();

        List<ChunkKey> first = keys(retrieval.retrieve("car invoice", plan));
        List<ChunkKey> second = keys(retrieval.retrieve("car invoice", plan));

        assertEquals(first, second);
    }

    @Test
    void semanticHitsAloneAreReturnedWhenLexicalSearchFindsNothing() throws IOException {
        HybridRetrieval retrieval = HybridRetrieval.lexical(new ScriptedLexicalIndex())
                .withSemantic(semanticIndex, embeddings);

        RetrievalResult result = retrieval.retrieve("automobile",
                HybridRetrievalPlan.builder(1).semantic(true).build());

        assertEquals(Collections.singletonList(vehicleNote), keys(result));
        assertEquals(ScoreSource.FUSED, result.hits().get(0).scoreSource());
    }

    @Test
    void emptySemanticIndexKeepsLexicalScores() throws IOException {
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical)
                .withSemantic(new InMemorySemanticIndex(embeddings.model()), embeddings);

        RetrievalResult result = retrieval.retrieve("car", HybridRetrievalPlan.builder(10).semantic(true).build());

        assertEquals(StageOutcome.NO_CANDIDATES, result.semanticOutcome());
        assertEquals(ScoreSource.LEXICAL, result.hits().get(0).scoreSource());
        assertEquals(Arrays.asList(invoiceIntro, invoiceTotals), keys(result));
    }

    @Test
    void embeddingFailureDegradesToLexicalResult() throws IOException {
        embeddings.failNextCall(new EmbeddingException("runtime offline"));
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical).withSemantic(semanticIndex, embeddings);

        RetrievalResult result = retrieval.retrieve("car", HybridRetrievalPlan.builder(10).semantic(true).build());

        assertEquals(StageOutcome.FAILED, result.semanticOutcome());
        assertTrue(result.semanticFailure().contains("runtime offline"));
        assertEquals(Arrays.asList(invoiceIntro, invoiceTotals), keys(result));
        assertEquals(ScoreSource.LEXICAL, result.hits().get(0).scoreSource());
    }

    @Test
    void clientOfAnotherModelIsNeverUsedAgainstTheIndex() throws IOException {
        TokenHashEmbeddingClient otherModel = new TokenHashEmbeddingClient(32);
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical).withSemantic(semanticIndex, otherModel);

        RetrievalResult result = retrieval.retrieve("car", HybridRetrievalPlan.builder(10).semantic(true).build());

        assertEquals(StageOutcome.MODEL_MISMATCH, result.semanticOutcome());
        assertTrue(otherModel.calls().isEmpty());
        assertEquals(Arrays.asList(invoiceIntro, invoiceTotals), keys(result));
    }

    @Test
    void clientBreakingTheBatchContractCountsAsFailure() throws IOException {
        EmbeddingClient broken = new EmbeddingClient() {
            @Override
            public EmbeddingModel model() {
                return embeddings.model();
            }

            @Override
            public List<EmbeddingVector> embed(EmbeddingPurpose purpose, List<String> texts) {
                return Collections.emptyList();
            }
        };
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical).withSemantic(semanticIndex, broken);

        RetrievalResult result = retrieval.retrieve("car", HybridRetrievalPlan.builder(10).semantic(true).build());

        assertEquals(StageOutcome.FAILED, result.semanticOutcome());
        assertEquals(Arrays.asList(invoiceIntro, invoiceTotals), keys(result));
    }

    @Test
    void rerankingWorksOnLexicalCandidatesWithoutEmbeddings() throws IOException {
        TokenOverlapReranker reranker = new TokenOverlapReranker();
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical).withReranker(reranker);

        RetrievalResult result = retrieval.retrieve("invoice totals",
                HybridRetrievalPlan.builder(10).rerank(true).build());

        assertEquals(StageOutcome.DISABLED, result.semanticOutcome());
        assertEquals(StageOutcome.APPLIED, result.rerankOutcome());
        assertEquals(Arrays.asList(invoiceTotals, invoiceIntro), keys(result));
        RetrievalHit top = result.hits().get(0);
        assertEquals(ScoreSource.RERANKED, top.scoreSource());
        assertEquals(1d, top.score(), 1e-12);
        assertEquals(2, top.lexicalRank());
    }

    @Test
    void rerankerReceivesAtMostTheConfiguredPool() throws IOException {
        ScriptedLexicalIndex many = new ScriptedLexicalIndex();
        for (int i = 0; i < 8; i++) {
            many.add(TestRefs.chunk("doc.txt", i), 8 - i, "passage " + i);
        }
        TokenOverlapReranker reranker = new TokenOverlapReranker();

        RetrievalResult result = HybridRetrieval.lexical(many).withReranker(reranker)
                .retrieve("passage", HybridRetrievalPlan.builder(2).rerank(true).rerankCandidates(5).build());

        assertEquals(5, reranker.requests().get(0).candidates().size());
        assertEquals(2, result.hits().size());
        // All five candidates tie at 1.0; the chunk key order decides.
        assertEquals(Arrays.asList(TestRefs.chunk("doc.txt", 0), TestRefs.chunk("doc.txt", 1)), keys(result));
    }

    @Test
    void rerankFailureKeepsThePreviousRanking() throws IOException {
        TokenOverlapReranker reranker = new TokenOverlapReranker();
        reranker.failNextCall(new RerankException("cross-encoder down"));

        RetrievalResult result = HybridRetrieval.lexical(lexical).withReranker(reranker)
                .retrieve("invoice totals", HybridRetrievalPlan.builder(1).rerank(true).build());

        assertEquals(StageOutcome.FAILED, result.rerankOutcome());
        assertTrue(result.rerankFailure().contains("cross-encoder down"));
        assertEquals(Collections.singletonList(invoiceIntro), keys(result));
        assertEquals(ScoreSource.LEXICAL, result.hits().get(0).scoreSource());
    }

    @Test
    void rerankerScoringUnknownCandidatesIsRejected() throws IOException {
        Reranker rogue = request -> Collections.singletonList(new RerankedResult(vehicleNote, 9d));

        RetrievalResult result = HybridRetrieval.lexical(lexical).withReranker(rogue)
                .retrieve("invoice", HybridRetrievalPlan.builder(10).rerank(true).build());

        assertEquals(StageOutcome.FAILED, result.rerankOutcome());
        assertEquals(Arrays.asList(invoiceIntro, invoiceTotals), keys(result));
    }

    @Test
    void rerankerScoringACandidateTwiceIsRejected() throws IOException {
        Reranker duplicating = request -> Arrays.asList(
                new RerankedResult(invoiceIntro, 1d), new RerankedResult(invoiceIntro, 2d));

        RetrievalResult result = HybridRetrieval.lexical(lexical).withReranker(duplicating)
                .retrieve("invoice", HybridRetrievalPlan.builder(10).rerank(true).build());

        assertEquals(StageOutcome.FAILED, result.rerankOutcome());
    }

    @Test
    void candidatesTheRerankerLeavesOutAreDropped() throws IOException {
        Reranker selective = request -> Collections.singletonList(new RerankedResult(invoiceTotals, 0.7d));

        RetrievalResult result = HybridRetrieval.lexical(lexical).withReranker(selective)
                .retrieve("invoice", HybridRetrievalPlan.builder(10).rerank(true).build());

        assertEquals(StageOutcome.APPLIED, result.rerankOutcome());
        assertEquals(Collections.singletonList(invoiceTotals), keys(result));
    }

    @Test
    void semanticWideningAndRerankingCombine() throws IOException {
        TokenOverlapReranker reranker = new TokenOverlapReranker();
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical)
                .withSemantic(semanticIndex, embeddings)
                .withReranker(reranker);

        RetrievalResult result = retrieval.retrieve("vehicle inspection",
                HybridRetrievalPlan.builder(1).semantic(true).rerank(true).build());

        assertEquals(StageOutcome.APPLIED, result.semanticOutcome());
        assertEquals(StageOutcome.APPLIED, result.rerankOutcome());
        assertEquals(Collections.singletonList(vehicleNote), keys(result));
        List<ChunkKey> reranked = new ArrayList<ChunkKey>();
        for (RerankCandidate candidate : reranker.requests().get(0).candidates()) {
            reranked.add(candidate.key());
        }
        assertTrue(reranked.contains(vehicleNote));
    }

    @Test
    void truncatesToTheFinalLimitAndAsksForTheLexicalPool() throws IOException {
        RetrievalResult result = HybridRetrieval.lexical(lexical)
                .retrieve("invoice", HybridRetrievalPlan.builder(1).lexicalCandidates(7).build());

        assertEquals(1, result.hits().size());
        assertEquals(7, lexical.queries().get(0).maxResults());
    }

    @Test
    void lexicalFailurePropagates() {
        IOException failure = new IOException("index unreadable");
        lexical.failWith(failure);

        IOException thrown = assertThrows(IOException.class, () -> HybridRetrieval.lexical(lexical)
                .retrieve("car", HybridRetrievalPlan.lexicalOnly(5)));
        assertSame(failure, thrown);
    }

    @Test
    void rejectsInvalidArguments() {
        HybridRetrieval retrieval = HybridRetrieval.lexical(lexical);

        assertThrows(IllegalArgumentException.class, () -> HybridRetrieval.lexical(null));
        assertThrows(IllegalArgumentException.class, () -> retrieval.retrieve(" ", HybridRetrievalPlan.lexicalOnly(1)));
        assertThrows(IllegalArgumentException.class, () -> retrieval.retrieve("car", null));
        assertThrows(IllegalArgumentException.class, () -> retrieval.withSemantic(null, embeddings));
        assertThrows(IllegalArgumentException.class, () -> retrieval.withReranker(null));
    }

    private static List<ChunkKey> keys(RetrievalResult result) {
        List<ChunkKey> keys = new ArrayList<ChunkKey>();
        for (RetrievalHit hit : result.hits()) {
            keys.add(hit.key());
        }
        return keys;
    }

    private static RetrievalHit hit(RetrievalResult result, ChunkKey key) {
        for (RetrievalHit hit : result.hits()) {
            if (hit.key().equals(key)) {
                return hit;
            }
        }
        return null;
    }
}
