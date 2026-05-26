package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

/**
 * Configuration for lexical chunking behavior.
 */
public final class LexicalChunkingConfig {

    /** Default max tokens per chunk. */
    public static final int DEFAULT_CHUNK_SIZE_TOKENS = 350;
    /** Default number of overlapping sentences between adjacent chunks. */
    public static final int DEFAULT_OVERLAP_SENTENCES = 1;

    private final int chunkSizeTokens;
    private final int overlapSentences;

    public LexicalChunkingConfig(int chunkSizeTokens, int overlapSentences) {
        if (chunkSizeTokens <= 0) throw new IllegalArgumentException("chunkSizeTokens must be positive");
        if (overlapSentences < 0) throw new IllegalArgumentException("overlapSentences must not be negative");
        this.chunkSizeTokens = chunkSizeTokens;
        this.overlapSentences = overlapSentences;
    }

    /** Creates a config with default values (350 tokens, 1 overlap sentence). */
    public LexicalChunkingConfig() {
        this(DEFAULT_CHUNK_SIZE_TOKENS, DEFAULT_OVERLAP_SENTENCES);
    }

    public int chunkSizeTokens() { return chunkSizeTokens; }
    public int overlapSentences() { return overlapSentences; }
}
