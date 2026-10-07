package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Report one chunk returned by hybrid retrieval.
 *
 * <p>Ranks are one-based positions in the lexical and the semantic candidate lists; a rank
 * of {@code 0} means the chunk was not found by that stage.
 */
public final class RetrievalHit {

    private final ChunkKey key;
    private final String text;
    private final String title;
    private final String contentType;
    private final double score;
    private final ScoreSource scoreSource;
    private final int lexicalRank;
    private final int semanticRank;

    public RetrievalHit(ChunkKey key, String text, String title, String contentType,
                        double score, ScoreSource scoreSource, int lexicalRank, int semanticRank) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        if (scoreSource == null) {
            throw new IllegalArgumentException("scoreSource must not be null");
        }
        if (lexicalRank < 0 || semanticRank < 0) {
            throw new IllegalArgumentException("ranks must not be negative");
        }
        if (lexicalRank == 0 && semanticRank == 0) {
            throw new IllegalArgumentException("A hit must come from the lexical or the semantic stage");
        }
        this.key = key;
        this.text = text;
        this.title = title;
        this.contentType = contentType;
        this.score = score;
        this.scoreSource = scoreSource;
        this.lexicalRank = lexicalRank;
        this.semanticRank = semanticRank;
    }

    /** Return the chunk identity. */
    public ChunkKey key() {
        return key;
    }

    /** Return the chunk text. */
    public String text() {
        return text;
    }

    /** Return the document title from the lexical register, or {@code null}. */
    public String title() {
        return title;
    }

    /** Return the content type from the lexical register, or {@code null}. */
    public String contentType() {
        return contentType;
    }

    /** Return the score of the stage named by {@link #scoreSource()}. */
    public double score() {
        return score;
    }

    /** Return the stage the score comes from. */
    public ScoreSource scoreSource() {
        return scoreSource;
    }

    /** Return the one-based lexical rank, or {@code 0} if not found lexically. */
    public int lexicalRank() {
        return lexicalRank;
    }

    /** Return the one-based semantic rank, or {@code 0} if not found semantically. */
    public int semanticRank() {
        return semanticRank;
    }

    @Override
    public String toString() {
        return "RetrievalHit{" + key + ", score=" + score + ", source=" + scoreSource
                + ", lexicalRank=" + lexicalRank + ", semanticRank=" + semanticRank + "}";
    }
}
