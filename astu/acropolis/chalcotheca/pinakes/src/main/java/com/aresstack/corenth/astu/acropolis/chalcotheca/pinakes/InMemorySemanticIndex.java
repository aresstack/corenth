package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provide the deterministic reference implementation of {@link SemanticIndex}.
 *
 * <p>The index keeps entries in memory and scores them by exact linear cosine search. It is
 * the contract reference for tests and small local use; it is neither persistent nor
 * meant to scale. Results with equal similarity are ordered by {@link ChunkKey} order, so
 * repeated searches return identical lists regardless of insertion order.
 */
public final class InMemorySemanticIndex implements SemanticIndex {

    private static final Comparator<SemanticSearchResult> BEST_FIRST = new Comparator<SemanticSearchResult>() {
        @Override
        public int compare(SemanticSearchResult a, SemanticSearchResult b) {
            int bySimilarity = Double.compare(b.similarity(), a.similarity());
            return bySimilarity != 0 ? bySimilarity : a.key().compareTo(b.key());
        }
    };

    private final EmbeddingModel model;
    private final Map<ChunkKey, SemanticEntry> entries = new LinkedHashMap<ChunkKey, SemanticEntry>();

    public InMemorySemanticIndex(EmbeddingModel model) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        this.model = model;
    }

    @Override
    public EmbeddingModel model() {
        return model;
    }

    @Override
    public synchronized void upsert(SemanticEntry entry) {
        requireStorable(entry);
        entries.put(entry.key(), entry);
    }

    @Override
    public synchronized void replaceResource(VirtualResourceRef resourceRef, List<SemanticEntry> newEntries) {
        if (resourceRef == null) {
            throw new IllegalArgumentException("resourceRef must not be null");
        }
        if (newEntries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        Set<ChunkKey> seen = new HashSet<ChunkKey>();
        for (SemanticEntry entry : newEntries) {
            requireStorable(entry);
            if (!entry.key().resourceRef().equals(resourceRef)) {
                throw new IllegalArgumentException("Entry " + entry.key() + " does not belong to " + resourceRef);
            }
            if (!seen.add(entry.key())) {
                throw new IllegalArgumentException("Duplicate chunk key " + entry.key());
            }
        }
        removeChunksOf(resourceRef);
        for (SemanticEntry entry : newEntries) {
            entries.put(entry.key(), entry);
        }
    }

    @Override
    public synchronized int removeResource(VirtualResourceRef resourceRef) {
        if (resourceRef == null) {
            throw new IllegalArgumentException("resourceRef must not be null");
        }
        return removeChunksOf(resourceRef);
    }

    @Override
    public synchronized List<SemanticSearchResult> search(EmbeddingVector query, int limit) {
        model.requireCompatible(query);
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (query.isZero()) {
            return Collections.emptyList();
        }
        List<SemanticSearchResult> scored = new ArrayList<SemanticSearchResult>(entries.size());
        for (SemanticEntry entry : entries.values()) {
            double similarity = query.cosineSimilarity(entry.vector());
            scored.add(new SemanticSearchResult(entry.key(), similarity, entry.text()));
        }
        Collections.sort(scored, BEST_FIRST);
        if (scored.size() > limit) {
            return Collections.unmodifiableList(new ArrayList<SemanticSearchResult>(scored.subList(0, limit)));
        }
        return Collections.unmodifiableList(scored);
    }

    @Override
    public synchronized int size() {
        return entries.size();
    }

    private void requireStorable(SemanticEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        model.requireCompatible(entry.vector());
        if (entry.vector().isZero()) {
            throw new IllegalArgumentException("Zero vector of " + entry.key() + " has no direction and cannot be retrieved");
        }
    }

    private int removeChunksOf(VirtualResourceRef resourceRef) {
        int removed = 0;
        Iterator<ChunkKey> keys = entries.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().resourceRef().equals(resourceRef)) {
                keys.remove();
                removed++;
            }
        }
        return removed;
    }
}
