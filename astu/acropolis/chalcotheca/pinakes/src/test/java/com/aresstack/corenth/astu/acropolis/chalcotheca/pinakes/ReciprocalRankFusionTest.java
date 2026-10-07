package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReciprocalRankFusionTest {

    private final ChunkKey a = TestRefs.chunk("a", 0);
    private final ChunkKey b = TestRefs.chunk("b", 0);
    private final ChunkKey c = TestRefs.chunk("c", 0);
    private final ChunkKey d = TestRefs.chunk("d", 0);

    @Test
    void sumsReciprocalRanksOverBothLists() {
        List<ReciprocalRankFusion.FusedRank> fused = new ReciprocalRankFusion(60)
                .fuse(Arrays.asList(a, b), Arrays.asList(b, c));

        assertEquals(b, fused.get(0).key());
        assertEquals(1d / 62 + 1d / 61, fused.get(0).score(), 1e-12);
        assertEquals(2, fused.get(0).lexicalRank());
        assertEquals(1, fused.get(0).semanticRank());
        assertEquals(a, fused.get(1).key());
        assertEquals(1, fused.get(1).lexicalRank());
        assertEquals(0, fused.get(1).semanticRank());
        assertEquals(c, fused.get(2).key());
        assertEquals(0, fused.get(2).lexicalRank());
    }

    @Test
    void breaksScoreTiesByBestRankThenChunkKey() {
        // d and a both score 1/61; c and b both score 1/62.
        List<ReciprocalRankFusion.FusedRank> fused = new ReciprocalRankFusion(60)
                .fuse(Arrays.asList(d, c), Arrays.asList(a, b));

        assertEquals(Arrays.asList(a, d, b, c), keys(fused));
        assertEquals(keys(fused), keys(new ReciprocalRankFusion(60).fuse(Arrays.asList(d, c), Arrays.asList(a, b))));
    }

    @Test
    void countsOnlyTheFirstOccurrenceWithinOneList() {
        List<ReciprocalRankFusion.FusedRank> fused = new ReciprocalRankFusion(10)
                .fuse(Arrays.asList(a, a, b), Collections.<ChunkKey>emptyList());

        assertEquals(2, fused.size());
        assertEquals(1d / 11, fused.get(0).score(), 1e-12);
        assertEquals(1d / 13, fused.get(1).score(), 1e-12);
        assertEquals(3, fused.get(1).lexicalRank());
    }

    @Test
    void handlesEmptyRankings() {
        assertTrue(new ReciprocalRankFusion(60).fuse(Collections.<ChunkKey>emptyList(),
                Collections.<ChunkKey>emptyList()).isEmpty());
        assertEquals(Arrays.asList(c, a), keys(new ReciprocalRankFusion(60)
                .fuse(Collections.<ChunkKey>emptyList(), Arrays.asList(c, a))));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new ReciprocalRankFusion(0));
        assertThrows(IllegalArgumentException.class, () -> new ReciprocalRankFusion(60).fuse(null,
                Collections.<ChunkKey>emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new ReciprocalRankFusion(60)
                .fuse(Arrays.asList(a, null), Collections.<ChunkKey>emptyList()));
    }

    private static List<ChunkKey> keys(List<ReciprocalRankFusion.FusedRank> fused) {
        ChunkKey[] keys = new ChunkKey[fused.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = fused.get(i).key();
        }
        return Arrays.asList(keys);
    }
}
