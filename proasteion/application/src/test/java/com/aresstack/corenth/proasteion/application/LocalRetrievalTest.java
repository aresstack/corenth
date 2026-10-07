package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.ProcessingResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalQuery;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.ChunkKey;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.EmbeddingPurpose;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.HybridRetrieval;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.HybridRetrievalPlan;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.InMemorySemanticIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.RetrievalHit;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.RetrievalResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.SemanticEntry;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.StageOutcome;
import com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes.TokenHashEmbeddingClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Bounded retrieval check (#7): resources indexed through the productive composition are
 * retrievable through Pinakes over the same lexical register, and a semantic stage maps its hits
 * onto the same chunk identity.
 *
 * <p>The semantic vectors come from the Pinakes contract fake {@link TokenHashEmbeddingClient};
 * this proves wiring and chunk identity, not embedding quality. Pinakes is not part of the host
 * composition yet.
 */
public class LocalRetrievalTest {

    private static final String VEHICLE_NOTE = "Every vehicle needs a yearly inspection.";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void composedLexicalIndex_isRetrievableThroughPinakes_lexicallyAndSemantically() throws Exception {
        Path root = tempFolder.newFolder("documents").toPath();
        Path indexDirectory = tempFolder.newFolder("index").toPath();
        Path note = Files.write(root.resolve("fleet.txt"), VEHICLE_NOTE.getBytes(StandardCharsets.UTF_8));
        Path guide = Files.write(root.resolve("guide.md"),
                "# Guide\n\nInstall the mainframe connector first.".getBytes(StandardCharsets.UTF_8));
        CorenthApplication application = new CorenthComposition().composeLocal(ApplicationSettings.builder()
                .indexDirectory(indexDirectory).accessibleRoot(root).build());
        try {
            assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(note)).status());
            assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(guide)).status());
        } finally {
            application.close();
        }

        LuceneLexicalIndex lexicalIndex = new LuceneLexicalIndex(new LexicalIndexConfig(indexDirectory));
        try {
            HybridRetrieval lexicalOnly = HybridRetrieval.lexical(lexicalIndex);
            RetrievalResult plain = lexicalOnly.retrieve("inspection", HybridRetrievalPlan.lexicalOnly(5));
            List<LexicalSearchResult> direct = lexicalIndex.search(new LexicalQuery("inspection", 5));
            assertEquals(1, plain.hits().size());
            assertEquals(ChunkKey.of(direct.get(0)), plain.hits().get(0).key());
            assertEquals(ref(note), plain.hits().get(0).key().resourceRef());
            assertEquals(StageOutcome.DISABLED, plain.semanticOutcome());

            TokenHashEmbeddingClient embeddings = new TokenHashEmbeddingClient(128).withSynonyms("car", "vehicle");
            InMemorySemanticIndex semanticIndex = new InMemorySemanticIndex(embeddings.model());
            ChunkKey noteChunk = ChunkKey.of(direct.get(0));
            semanticIndex.replaceResource(ref(note), Collections.singletonList(new SemanticEntry(noteChunk,
                    embeddings.embed(EmbeddingPurpose.PASSAGE, Collections.singletonList(VEHICLE_NOTE)).get(0),
                    VEHICLE_NOTE)));

            assertTrue("no lexical match for the synonym",
                    lexicalOnly.retrieve("car", HybridRetrievalPlan.lexicalOnly(5)).hits().isEmpty());
            RetrievalResult widened = lexicalOnly.withSemantic(semanticIndex, embeddings)
                    .retrieve("car", HybridRetrievalPlan.builder(3).semantic(true).build());
            assertEquals(StageOutcome.APPLIED, widened.semanticOutcome());
            RetrievalHit top = widened.hits().get(0);
            assertEquals(noteChunk, top.key());
            assertTrue(top.semanticRank() > 0);
        } finally {
            lexicalIndex.close();
        }
    }

    private static VirtualResourceRef ref(Path path) {
        return new VirtualResourceRef(BookmarkUri.parse(path.toUri().toString()), VirtualResourceKind.FILE);
    }
}
