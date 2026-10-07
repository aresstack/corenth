package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Name the stage whose score a retrieval hit carries.
 */
public enum ScoreSource {
    /** The lexical relevance score from Anagraphai, unchanged. */
    LEXICAL,
    /** A reciprocal rank fusion score of the lexical and the semantic ranking. */
    FUSED,
    /** The relevance score a reranker assigned. */
    RERANKED
}
