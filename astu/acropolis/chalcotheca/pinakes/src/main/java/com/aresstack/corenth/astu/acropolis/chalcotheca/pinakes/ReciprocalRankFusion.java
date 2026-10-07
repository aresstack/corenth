package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuse a lexical and a semantic ranking with reciprocal rank fusion.
 *
 * <p>Each chunk scores {@code 1 / (k + rank)} per list it appears in, summed over both
 * lists. Fusion uses ranks only, so BM25 scores and cosine similarities never have to be
 * normalised against each other. The function performs no I/O and is fully deterministic:
 * ties are broken by the better single rank, then by {@link ChunkKey} order. When a key
 * occurs more than once in one list, only its first (best) position counts.
 */
public final class ReciprocalRankFusion {

    /** Use the rank constant established in the original RRF paper. */
    public static final int DEFAULT_RANK_CONSTANT = 60;

    private static final Comparator<FusedRank> BEST_FIRST = new Comparator<FusedRank>() {
        @Override
        public int compare(FusedRank a, FusedRank b) {
            int byScore = Double.compare(b.score(), a.score());
            if (byScore != 0) {
                return byScore;
            }
            int byBestRank = a.bestRank() < b.bestRank() ? -1 : (a.bestRank() == b.bestRank() ? 0 : 1);
            return byBestRank != 0 ? byBestRank : a.key().compareTo(b.key());
        }
    };

    private final int rankConstant;

    public ReciprocalRankFusion(int rankConstant) {
        if (rankConstant < 1) {
            throw new IllegalArgumentException("rankConstant must be at least 1");
        }
        this.rankConstant = rankConstant;
    }

    /** Return the rank constant {@code k}. */
    public int rankConstant() {
        return rankConstant;
    }

    /**
     * Fuse two rankings, best first.
     *
     * @param lexicalRanking  chunk keys in lexical order, best first; may be empty
     * @param semanticRanking chunk keys in semantic order, best first; may be empty
     */
    public List<FusedRank> fuse(List<ChunkKey> lexicalRanking, List<ChunkKey> semanticRanking) {
        if (lexicalRanking == null || semanticRanking == null) {
            throw new IllegalArgumentException("rankings must not be null");
        }
        Map<ChunkKey, int[]> ranks = new LinkedHashMap<ChunkKey, int[]>();
        collectRanks(lexicalRanking, ranks, 0);
        collectRanks(semanticRanking, ranks, 1);

        List<FusedRank> fused = new ArrayList<FusedRank>(ranks.size());
        for (Map.Entry<ChunkKey, int[]> entry : ranks.entrySet()) {
            int lexicalRank = entry.getValue()[0];
            int semanticRank = entry.getValue()[1];
            fused.add(new FusedRank(entry.getKey(), contribution(lexicalRank) + contribution(semanticRank),
                    lexicalRank, semanticRank));
        }
        Collections.sort(fused, BEST_FIRST);
        return Collections.unmodifiableList(fused);
    }

    private double contribution(int rank) {
        return rank == 0 ? 0d : 1d / (rankConstant + rank);
    }

    private static void collectRanks(List<ChunkKey> ranking, Map<ChunkKey, int[]> ranks, int slot) {
        for (int position = 0; position < ranking.size(); position++) {
            ChunkKey key = ranking.get(position);
            if (key == null) {
                throw new IllegalArgumentException("ranking must not contain null keys");
            }
            int[] slots = ranks.get(key);
            if (slots == null) {
                slots = new int[2];
                ranks.put(key, slots);
            }
            if (slots[slot] == 0) {
                slots[slot] = position + 1;
            }
        }
    }

    /**
     * Report the fused score and the one-based source ranks of one chunk.
     */
    public static final class FusedRank {

        private final ChunkKey key;
        private final double score;
        private final int lexicalRank;
        private final int semanticRank;

        FusedRank(ChunkKey key, double score, int lexicalRank, int semanticRank) {
            this.key = key;
            this.score = score;
            this.lexicalRank = lexicalRank;
            this.semanticRank = semanticRank;
        }

        /** Return the chunk identity. */
        public ChunkKey key() {
            return key;
        }

        /** Return the fused score. */
        public double score() {
            return score;
        }

        /** Return the one-based lexical rank, or {@code 0}. */
        public int lexicalRank() {
            return lexicalRank;
        }

        /** Return the one-based semantic rank, or {@code 0}. */
        public int semanticRank() {
            return semanticRank;
        }

        int bestRank() {
            if (lexicalRank == 0) {
                return semanticRank;
            }
            if (semanticRank == 0) {
                return lexicalRank;
            }
            return Math.min(lexicalRank, semanticRank);
        }

        @Override
        public String toString() {
            return "FusedRank{" + key + ", score=" + score + ", lexicalRank=" + lexicalRank
                    + ", semanticRank=" + semanticRank + "}";
        }
    }
}
