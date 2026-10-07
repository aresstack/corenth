package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.List;

/**
 * Port for storing chunk vectors and finding the chunks nearest to a query vector.
 *
 * <p>An index is bound to exactly one {@link EmbeddingModel}. Every write and query is
 * checked against that model's dimension. Writes are visible to the next search; an
 * adapter that buffers writes must flush before it returns.
 *
 * <p>The index never decides <em>whether</em> a resource should be indexed or withdrawn;
 * the lifecycle orchestration does, based on policy decisions made elsewhere.
 */
public interface SemanticIndex {

    /** Return the model this index is bound to. */
    EmbeddingModel model();

    /**
     * Insert or replace one chunk.
     *
     * @throws IllegalArgumentException if the vector does not fit the model or is a zero vector
     */
    void upsert(SemanticEntry entry);

    /**
     * Replace every chunk of a resource with the given entries in one step.
     *
     * <p>Chunks of the resource that are not part of {@code entries} are removed, so a
     * re-chunked resource leaves no stale chunks behind. An empty list removes the resource.
     *
     * @throws IllegalArgumentException if an entry belongs to another resource, a key is
     *                                  duplicated, or a vector is invalid; the index is then unchanged
     */
    void replaceResource(VirtualResourceRef resourceRef, List<SemanticEntry> entries);

    /**
     * Remove every chunk of a resource.
     *
     * @return the number of removed chunks
     */
    int removeResource(VirtualResourceRef resourceRef);

    /**
     * Return the chunks nearest to the query, best first.
     *
     * <p>Ties are ordered by {@link ChunkKey} order. A zero query vector has no direction
     * and yields an empty result.
     *
     * @param query the query vector of the bound model
     * @param limit the maximum number of results, at least 1
     * @throws IllegalArgumentException if the query does not fit the model or limit is below 1
     */
    List<SemanticSearchResult> search(EmbeddingVector query, int limit);

    /** Return the number of stored chunks. */
    int size();
}
