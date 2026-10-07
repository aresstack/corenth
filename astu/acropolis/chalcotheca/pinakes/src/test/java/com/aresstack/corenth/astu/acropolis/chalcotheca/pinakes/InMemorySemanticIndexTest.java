package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySemanticIndexTest {

    private final EmbeddingModel model = new EmbeddingModel("test", 2);
    private final InMemorySemanticIndex index = new InMemorySemanticIndex(model);

    private static SemanticEntry entry(ChunkKey key, float x, float y) {
        return new SemanticEntry(key, TestRefs.vector(x, y), "text of " + key.chunkIndex());
    }

    @Test
    void rejectsVectorsOfAnotherDimension() {
        assertThrows(IllegalArgumentException.class, () ->
                index.upsert(new SemanticEntry(TestRefs.chunk("a", 0), TestRefs.vector(1f, 0f, 0f), "t")));
        assertThrows(IllegalArgumentException.class, () -> index.search(TestRefs.vector(1f), 5));
        assertEquals(0, index.size());
    }

    @Test
    void rejectsZeroVectorsOnWrite() {
        assertThrows(IllegalArgumentException.class, () -> index.upsert(entry(TestRefs.chunk("a", 0), 0f, 0f)));
        assertEquals(0, index.size());
    }

    @Test
    void returnsNothingForAZeroQuery() {
        index.upsert(entry(TestRefs.chunk("a", 0), 1f, 0f));

        assertTrue(index.search(TestRefs.vector(0f, 0f), 5).isEmpty());
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> index.search(TestRefs.vector(1f, 0f), 0));
    }

    @Test
    void ranksBySimilarityAndRespectsLimit() {
        index.upsert(entry(TestRefs.chunk("far", 0), 0f, 1f));
        index.upsert(entry(TestRefs.chunk("near", 0), 1f, 0.1f));
        index.upsert(entry(TestRefs.chunk("mid", 0), 1f, 1f));

        List<SemanticSearchResult> results = index.search(TestRefs.vector(1f, 0f), 2);

        assertEquals(2, results.size());
        assertEquals(TestRefs.chunk("near", 0), results.get(0).key());
        assertEquals(TestRefs.chunk("mid", 0), results.get(1).key());
        assertTrue(results.get(0).similarity() > results.get(1).similarity());
        assertEquals("text of 0", results.get(0).text());
    }

    @Test
    void breaksTiesByChunkKeyIndependentOfInsertionOrder() {
        List<ChunkKey> keys = Arrays.asList(
                TestRefs.chunk("c", 0), TestRefs.chunk("a", 1), TestRefs.chunk("b", 0), TestRefs.chunk("a", 0));
        List<ChunkKey> expected = new ArrayList<ChunkKey>(keys);
        Collections.sort(expected);

        for (int attempt = 0; attempt < 4; attempt++) {
            InMemorySemanticIndex fresh = new InMemorySemanticIndex(model);
            List<ChunkKey> insertion = new ArrayList<ChunkKey>(keys);
            Collections.rotate(insertion, attempt);
            for (ChunkKey key : insertion) {
                fresh.upsert(entry(key, 2f, 2f));
            }

            List<ChunkKey> found = new ArrayList<ChunkKey>();
            for (SemanticSearchResult result : fresh.search(TestRefs.vector(1f, 1f), 10)) {
                found.add(result.key());
            }
            assertEquals(expected, found);
        }
    }

    @Test
    void upsertReplacesTheSameChunk() {
        ChunkKey key = TestRefs.chunk("a", 0);
        index.upsert(entry(key, 1f, 0f));
        index.upsert(new SemanticEntry(key, TestRefs.vector(0f, 1f), "new text"));

        List<SemanticSearchResult> results = index.search(TestRefs.vector(0f, 1f), 5);

        assertEquals(1, index.size());
        assertEquals(1d, results.get(0).similarity(), 1e-12);
        assertEquals("new text", results.get(0).text());
    }

    @Test
    void replaceResourceDropsStaleChunksOfThatResourceOnly() {
        index.upsert(entry(TestRefs.chunk("a", 0), 1f, 0f));
        index.upsert(entry(TestRefs.chunk("a", 1), 1f, 0f));
        index.upsert(entry(TestRefs.chunk("a", 2), 1f, 0f));
        index.upsert(entry(TestRefs.chunk("b", 0), 1f, 0f));

        index.replaceResource(TestRefs.file("a"), Collections.singletonList(entry(TestRefs.chunk("a", 0), 0f, 1f)));

        assertEquals(2, index.size());
        List<ChunkKey> found = new ArrayList<ChunkKey>();
        for (SemanticSearchResult result : index.search(TestRefs.vector(1f, 1f), 10)) {
            found.add(result.key());
        }
        assertEquals(Arrays.asList(TestRefs.chunk("a", 0), TestRefs.chunk("b", 0)), found);
    }

    @Test
    void replaceResourceWithEmptyListRemovesTheResource() {
        index.upsert(entry(TestRefs.chunk("a", 0), 1f, 0f));

        index.replaceResource(TestRefs.file("a"), Collections.<SemanticEntry>emptyList());

        assertEquals(0, index.size());
    }

    @Test
    void replaceResourceLeavesIndexUnchangedOnInvalidInput() {
        index.upsert(entry(TestRefs.chunk("a", 0), 1f, 0f));

        assertThrows(IllegalArgumentException.class, () -> index.replaceResource(TestRefs.file("a"),
                Arrays.asList(entry(TestRefs.chunk("a", 1), 1f, 0f), entry(TestRefs.chunk("b", 0), 1f, 0f))));
        assertThrows(IllegalArgumentException.class, () -> index.replaceResource(TestRefs.file("a"),
                Arrays.asList(entry(TestRefs.chunk("a", 1), 1f, 0f), entry(TestRefs.chunk("a", 1), 0f, 1f))));
        assertThrows(IllegalArgumentException.class, () -> index.replaceResource(TestRefs.file("a"),
                Collections.singletonList(entry(TestRefs.chunk("a", 1), 0f, 0f))));

        assertEquals(1, index.size());
        assertEquals(TestRefs.chunk("a", 0), index.search(TestRefs.vector(1f, 0f), 1).get(0).key());
    }

    @Test
    void removeResourceRemovesAllItsChunks() {
        index.upsert(entry(TestRefs.chunk("a", 0), 1f, 0f));
        index.upsert(entry(TestRefs.chunk("a", 1), 1f, 0f));
        index.upsert(entry(TestRefs.chunk("b", 0), 1f, 0f));

        assertEquals(2, index.removeResource(TestRefs.file("a")));
        assertEquals(0, index.removeResource(TestRefs.file("a")));
        assertEquals(1, index.size());
        assertEquals(TestRefs.chunk("b", 0), index.search(TestRefs.vector(1f, 0f), 5).get(0).key());
    }

    @Test
    void returnsUnmodifiableResults() {
        index.upsert(entry(TestRefs.chunk("a", 0), 1f, 0f));
        List<SemanticSearchResult> results = index.search(TestRefs.vector(1f, 0f), 5);

        assertThrows(UnsupportedOperationException.class, () -> results.clear());
    }
}
