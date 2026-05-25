package com.aresstack.corenth.anagraphai;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * A single result from a lexical search.
 *
 * <p>Contains the matched resource reference, relevance score, chunk index,
 * and the text excerpt that matched the query.
 *
 * <p>Adapted from MainframeMate's {@code ScoredChunk} concept, using Corenth
 * resource identity instead of raw document paths.
 */
public final class LexicalSearchResult {

    private final VirtualResourceRef resourceRef;
    private final float score;
    private final int chunkIndex;
    private final String excerpt;
    private final String title;

    public LexicalSearchResult(VirtualResourceRef resourceRef, float score,
                               int chunkIndex, String excerpt, String title) {
        this.resourceRef = resourceRef;
        this.score = score;
        this.chunkIndex = chunkIndex;
        this.excerpt = excerpt;
        this.title = title;
    }

    /** Returns the resource reference that this result belongs to. */
    public VirtualResourceRef resourceRef() {
        return resourceRef;
    }

    /** Returns the BM25 relevance score. */
    public float score() {
        return score;
    }

    /** Returns the chunk index within the document. */
    public int chunkIndex() {
        return chunkIndex;
    }

    /** Returns the text excerpt from the matching chunk. */
    public String excerpt() {
        return excerpt;
    }

    /** Returns the document title, or {@code null} if not indexed. */
    public String title() {
        return title;
    }

    @Override
    public String toString() {
        return "LexicalSearchResult{score=" + score + ", chunk=" + chunkIndex
                + ", resource=" + resourceRef + "}";
    }
}
