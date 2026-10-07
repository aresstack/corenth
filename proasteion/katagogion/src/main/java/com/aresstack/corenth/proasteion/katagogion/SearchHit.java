package com.aresstack.corenth.proasteion.katagogion;

import com.aresstack.corenth.astu.VirtualResourceRef;

/** Immutable search hit exposed to tools, keyed by the canonical Corenth resource identity. */
public final class SearchHit {

    private final VirtualResourceRef resourceRef;
    private final String title;
    private final String excerpt;
    private final float score;
    private final int chunkIndex;

    public SearchHit(VirtualResourceRef resourceRef, String title, String excerpt, float score, int chunkIndex) {
        if (resourceRef == null) {
            throw new IllegalArgumentException("resourceRef must not be null");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        this.resourceRef = resourceRef;
        this.title = Names.nullToEmpty(title);
        this.excerpt = Names.nullToEmpty(excerpt);
        this.score = score;
        this.chunkIndex = chunkIndex;
    }

    public VirtualResourceRef resourceRef() {
        return resourceRef;
    }

    /** Return the indexed title or the empty string. */
    public String title() {
        return title;
    }

    /** Return the stored text of the matching chunk. */
    public String excerpt() {
        return excerpt;
    }

    public float score() {
        return score;
    }

    public int chunkIndex() {
        return chunkIndex;
    }

    @Override
    public String toString() {
        return "SearchHit{" + resourceRef + ", chunk=" + chunkIndex + ", score=" + score + "}";
    }
}
