package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercise the retrieval contract against the real Anagraphai register.
 *
 * <p>Semantic vectors come from {@link TokenHashEmbeddingClient}; this is contract coverage
 * of chunk identity and stage wiring, not evidence of real embedding quality.
 */
class LuceneBackedHybridRetrievalTest {

    @TempDir
    Path indexDirectory;

    @Test
    void sharesChunkIdentityWithTheLexicalRegisterAndKeepsLexicalSearchIntact() throws Exception {
        VirtualResourceRef guide = TestRefs.file("guide.txt");
        VirtualResourceRef fleet = TestRefs.file("fleet.txt");
        LexicalChunk[] guideChunks = {
                new LexicalChunk(0, "Install the mainframe connector first."),
                new LexicalChunk(1, "Then configure the job entry subsystem.")};
        LexicalChunk[] fleetChunks = {new LexicalChunk(0, "Every vehicle needs a yearly inspection.")};

        TokenHashEmbeddingClient embeddings = new TokenHashEmbeddingClient(128).withSynonyms("car", "vehicle");
        InMemorySemanticIndex semanticIndex = new InMemorySemanticIndex(embeddings.model());

        try (LuceneLexicalIndex lexicalIndex = new LuceneLexicalIndex(new LexicalIndexConfig(indexDirectory))) {
            indexBoth(lexicalIndex, semanticIndex, embeddings, guide, "Guide", guideChunks);
            indexBoth(lexicalIndex, semanticIndex, embeddings, fleet, "Fleet", fleetChunks);
            lexicalIndex.commit();

            HybridRetrieval lexicalOnly = HybridRetrieval.lexical(lexicalIndex);
            RetrievalResult plain = lexicalOnly.retrieve("job subsystem", HybridRetrievalPlan.lexicalOnly(5));
            List<LexicalSearchResult> direct = lexicalIndex.search(
                    new com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalQuery("job subsystem", 5));

            assertEquals(direct.size(), plain.hits().size());
            for (int i = 0; i < direct.size(); i++) {
                assertEquals(ChunkKey.of(direct.get(i)), plain.hits().get(i).key());
                assertEquals(direct.get(i).score(), plain.hits().get(i).score(), 1e-6);
            }
            assertEquals(new ChunkKey(guide, 1), plain.hits().get(0).key());

            RetrievalResult widened = lexicalOnly.withSemantic(semanticIndex, embeddings)
                    .retrieve("car inspection", HybridRetrievalPlan.builder(3).semantic(true).build());
            assertEquals(StageOutcome.APPLIED, widened.semanticOutcome());
            RetrievalHit top = widened.hits().get(0);
            assertEquals(new ChunkKey(fleet, 0), top.key());
            assertTrue(top.lexicalRank() > 0, "the shared word 'inspection' is found lexically");
            assertTrue(top.semanticRank() > 0, "the same chunk is found semantically under the same key");
            assertEquals("Fleet", top.title());

            lexicalIndex.remove(fleet);
            lexicalIndex.commit();
            semanticIndex.removeResource(fleet);
            RetrievalResult afterRemoval = lexicalOnly.withSemantic(semanticIndex, embeddings)
                    .retrieve("car inspection", HybridRetrievalPlan.builder(3).semantic(true).build());
            for (RetrievalHit hit : afterRemoval.hits()) {
                assertTrue(!hit.key().resourceRef().equals(fleet), "removed resource must not be returned");
            }
        }
    }

    private static void indexBoth(LuceneLexicalIndex lexicalIndex, InMemorySemanticIndex semanticIndex,
                                  TokenHashEmbeddingClient embeddings, VirtualResourceRef ref, String title,
                                  LexicalChunk... chunks) throws Exception {
        LexicalDocument.Builder document = LexicalDocument.builder(ref).title(title).contentType("text/plain");
        List<SemanticEntry> entries = new ArrayList<SemanticEntry>();
        for (LexicalChunk chunk : chunks) {
            document.addChunk(chunk);
            EmbeddingVector vector = embeddings.embed(EmbeddingPurpose.PASSAGE,
                    Collections.singletonList(chunk.text())).get(0);
            entries.add(new SemanticEntry(ChunkKey.of(ref, chunk), vector, chunk.text()));
        }
        lexicalIndex.index(document.build());
        semanticIndex.replaceResource(ref, entries);
    }
}
