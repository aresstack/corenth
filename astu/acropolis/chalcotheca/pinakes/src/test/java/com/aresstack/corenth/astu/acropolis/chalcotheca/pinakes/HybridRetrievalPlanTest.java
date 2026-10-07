package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrievalPlanTest {

    @Test
    void startsLexicalOnly() {
        HybridRetrievalPlan plan = HybridRetrievalPlan.lexicalOnly(5);

        assertEquals(5, plan.finalLimit());
        assertFalse(plan.semanticEnabled());
        assertFalse(plan.rerankEnabled());
        assertEquals(HybridRetrievalPlan.DEFAULT_LEXICAL_CANDIDATES, plan.lexicalCandidates());
        assertEquals(ReciprocalRankFusion.DEFAULT_RANK_CONSTANT, plan.fusionRankConstant());
    }

    @Test
    void switchesStagesIndependently() {
        HybridRetrievalPlan rerankOnly = HybridRetrievalPlan.builder(3).rerank(true).build();
        HybridRetrievalPlan semanticOnly = HybridRetrievalPlan.builder(3).semantic(true).build();

        assertTrue(rerankOnly.rerankEnabled());
        assertFalse(rerankOnly.semanticEnabled());
        assertTrue(semanticOnly.semanticEnabled());
        assertFalse(semanticOnly.rerankEnabled());
    }

    @Test
    void neverHandsTheRerankerFewerCandidatesThanTheFinalLimit() {
        HybridRetrievalPlan plan = HybridRetrievalPlan.builder(10).rerankCandidates(4).build();

        assertEquals(10, plan.rerankCandidates());
    }

    @Test
    void widensLexicalCandidatesToTheFinalLimit() {
        assertEquals(80, HybridRetrievalPlan.lexicalOnly(80).lexicalCandidates());
    }

    @Test
    void rejectsNonPositiveSizes() {
        assertThrows(IllegalArgumentException.class, () -> HybridRetrievalPlan.builder(0));
        assertThrows(IllegalArgumentException.class, () -> HybridRetrievalPlan.builder(1).lexicalCandidates(0));
        assertThrows(IllegalArgumentException.class, () -> HybridRetrievalPlan.builder(1).semanticCandidates(0));
        assertThrows(IllegalArgumentException.class, () -> HybridRetrievalPlan.builder(1).rerankCandidates(0));
        assertThrows(IllegalArgumentException.class, () -> HybridRetrievalPlan.builder(1).fusionRankConstant(0));
    }
}
