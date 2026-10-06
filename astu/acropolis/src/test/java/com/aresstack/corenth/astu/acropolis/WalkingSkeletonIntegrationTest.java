package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionPort;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.BreakIteratorSentenceSegmenter;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LexicalChunker;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LexicalChunkingConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LuceneTokenCounter;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.NlpTextChunker;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import com.aresstack.corenth.proasteion.emporion.holkas.DefaultResourceConnectorRegistry;
import com.aresstack.corenth.proasteion.emporion.holkas.FileSystemResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.HolkasAcquisitionPort;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration test proving the full walking skeleton path through the mediated bronze archive
 * counter:
 * file: URI → acropolis lifecycle → MediatedResourceAccess (MediatedResourceService) → Tamias
 * access decision → AcquisitionPort (HolkasAcquisitionPort, FileSystemResourceConnector internally)
 * → deigma → tamias indexing policy → chalcotheca snapshot → anagraphai → search result.
 *
 * <p>This test is the composition layer: it wires outer adapter implementations (holkas, deigma)
 * to the inner contracts. Production code in {@code acropolis} never compiles against
 * {@code proasteion} packages and never touches connectors or the acquisition port.
 */
public class WalkingSkeletonIntegrationTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final ActorIdentity LIFECYCLE_ACTOR = new ActorIdentity("walking-skeleton", ActorType.SERVICE);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private LuceneLexicalIndex lexicalIndex;
    private MediatedResourceService counter;
    private ResourceLifecycleCoordinator coordinator;
    private SearchCoordinator searchCoordinator;

    @Before
    public void setUp() throws IOException {
        // Lucene index in temp directory
        File indexDir = tempFolder.newFolder("lucene-index");
        LexicalIndexConfig indexConfig = new LexicalIndexConfig(indexDir.toPath());
        lexicalIndex = new LuceneLexicalIndex(indexConfig);

        // Adapter: deigma detection + extraction → ContentInspector port
        ContentInspector inspector = createDeigmaInspector();

        // Tamias: indexing policy allowing .txt and .md files under 1MB
        IndexingRule textRule = new IndexingRule(
                "file-text-documents",
                Arrays.asList("file"),
                Arrays.asList("**/*.txt", "**/*.md"),
                Arrays.asList("**/.git/**", "**/target/**", "**/build/**"),
                1048576 // 1 MB
        );
        PatternResourcePolicy policy = new PatternResourcePolicy(Arrays.asList(textRule));

        // Chalcotheca: in-memory archive, shared by counter and lifecycle
        InMemoryResourceArchive archive = new InMemoryResourceArchive();

        // Anagraphai: lexical chunker with sentence-aware splitting
        LexicalChunker chunker = new NlpTextChunker(
                new BreakIteratorSentenceSegmenter(),
                new LuceneTokenCounter(),
                new LexicalChunkingConfig());

        // Chalcotheca: the archive counter over the real file connector
        counter = mediatedFileAccess(archive);

        // Acropolis: coordinator over the mediated counter, and search
        coordinator = new ResourceLifecycleCoordinator(
                counter, LIFECYCLE_ACTOR, inspector, policy, archive, lexicalIndex, chunker);
        searchCoordinator = new SearchCoordinator(lexicalIndex);
    }

    @After
    public void tearDown() throws IOException {
        if (lexicalIndex != null) {
            lexicalIndex.close();
        }
    }

    @Test
    public void fullPathProcessAndSearch_txtFile() throws IOException {
        File txtFile = tempFolder.newFile("architecture-notes.txt");
        writeFile(txtFile, "The Corenth architecture uses a walking skeleton approach "
                + "to prove the full pipeline end to end.");

        VirtualResourceRef ref = fileRef(txtFile);

        ProcessingResult result = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, result.status());

        List<LexicalSearchResult> results = searchCoordinator.search("architecture", 10);
        assertFalse("Expected at least one search result", results.isEmpty());
        assertEquals(ref, results.get(0).resourceRef());
        assertTrue(results.get(0).excerpt().contains("architecture"));
    }

    @Test
    public void fullPathProcessAndSearch_mdFile() throws IOException {
        File mdFile = tempFolder.newFile("readme.md");
        writeFile(mdFile, "# Corenth README\n\nThis project implements modular resource indexing.");

        VirtualResourceRef ref = fileRef(mdFile);
        ProcessingResult result = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, result.status());

        List<LexicalSearchResult> results = searchCoordinator.search("modular", 10);
        assertFalse("Expected at least one search result", results.isEmpty());
        assertEquals(ref, results.get(0).resourceRef());
    }

    @Test
    public void fullPath_bronzeContentIsCachedByTheArchiveCounter() throws IOException {
        File txtFile = tempFolder.newFile("counter-cached.txt");
        writeFile(txtFile, "Content served by the bronze archive counter.");
        VirtualResourceRef ref = fileRef(txtFile);

        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        MediatedResourceService counter = mediatedFileAccess(archive);
        ResourceLifecycleCoordinator counterCoordinator = new ResourceLifecycleCoordinator(
                counter, LIFECYCLE_ACTOR, createDeigmaInspector(), allowTextRule(), archive, lexicalIndex);

        assertFalse(counter.hasCachedContent(ref.uri()));
        assertEquals(ProcessingResult.Status.INDEXED, counterCoordinator.process(ref).status());
        assertTrue("the lifecycle read must have gone through the counter", counter.hasCachedContent(ref.uri()));
    }

    @Test
    public void searchWithNonPositiveMaxResults_returnsEmpty() throws IOException {
        File txtFile = tempFolder.newFile("queryable.txt");
        writeFile(txtFile, "searchable text");
        ProcessingResult result = coordinator.process(fileRef(txtFile));
        assertEquals(ProcessingResult.Status.INDEXED, result.status());

        assertTrue(searchCoordinator.search("searchable", 0).isEmpty());
        assertTrue(searchCoordinator.search("searchable", -1).isEmpty());
    }

    @Test
    public void policyDenies_unsupportedExtension() throws IOException {
        File pdfFile = tempFolder.newFile("document.pdf");
        writeFile(pdfFile, "fake pdf content");

        VirtualResourceRef ref = fileRef(pdfFile);
        ProcessingResult result = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("no matching rule"));
    }

    @Test
    public void policyDenies_excludedPath() throws IOException {
        File gitDir = tempFolder.newFolder(".git");
        File gitFile = new File(gitDir, "config.txt");
        writeFile(gitFile, "git config content");

        VirtualResourceRef ref = fileRef(gitFile);
        ProcessingResult result = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("excluded"));
    }

    @Test
    public void policyDenies_excludedPath_beforeAnyMediatedRead() throws IOException {
        File gitDir = tempFolder.newFolder(".git-early");
        File gitFile = new File(gitDir, "config.txt");
        writeFile(gitFile, "git config content");
        VirtualResourceRef ref = fileRef(gitFile);

        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        MediatedResourceService counter = mediatedFileAccess(archive);
        PatternResourcePolicy excluding = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "exclude-git-early", Arrays.asList("file"), Arrays.asList("**/*.txt"),
                Arrays.asList("**/.git-early/**"), Long.MAX_VALUE)));
        ResourceLifecycleCoordinator excludingCoordinator = new ResourceLifecycleCoordinator(
                counter, LIFECYCLE_ACTOR, createDeigmaInspector(), excluding, archive, lexicalIndex);

        ProcessingResult result = excludingCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertFalse("an excluded resource must not be acquired at all", counter.hasCachedContent(ref.uri()));
    }

    /**
     * Documents a guarantee weakened by #10 Slice 1: before the slice the skeleton denied
     * oversized files before fetching them (size probe on the direct provider). The mediated
     * contract has no size probe, so the counter acquires and retains the content and the
     * lifecycle denies it only afterwards. When a {@code READ_METADATA} operation exists
     * (#5/#33), this test must be inverted (expect no cached content).
     */
    @Test
    public void knownGap_oversizedFile_isAcquiredAndRetainedByCounterBeforeDenial() throws IOException {
        IndexingRule tinyRule = new IndexingRule(
                "tiny-rule",
                Arrays.asList("file"),
                Arrays.asList("**/*.txt"),
                Collections.<String>emptyList(),
                10 // only 10 bytes allowed
        );
        PatternResourcePolicy tinyPolicy = new PatternResourcePolicy(Arrays.asList(tinyRule));

        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        MediatedResourceService tinyCounter = mediatedFileAccess(archive);
        ResourceLifecycleCoordinator tinyCoordinator = new ResourceLifecycleCoordinator(
                tinyCounter, LIFECYCLE_ACTOR, createDeigmaInspector(), tinyPolicy, archive, lexicalIndex);

        File bigFile = tempFolder.newFile("big.txt");
        writeFile(bigFile, "This content is definitely larger than 10 bytes.");

        VirtualResourceRef ref = fileRef(bigFile);
        ProcessingResult result = tinyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("maxBytes"));
        assertTrue("size is only known after the counter acquired the content, which it retains",
                tinyCounter.hasCachedContent(ref.uri()));
    }

    @Test
    public void deniedAfterPreviouslyIndexed_removesStaleSearchEntry() throws IOException {
        File txtFile = tempFolder.newFile("stale-deny.txt");
        writeFile(txtFile, "stale term before deny");
        VirtualResourceRef ref = fileRef(txtFile);

        ProcessingResult indexed = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, indexed.status());
        assertFalse(searchCoordinator.search("stale", 10).isEmpty());

        PatternResourcePolicy denyPolicy = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "md-only", Arrays.asList("file"), Arrays.asList("**/*.md"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        ResourceLifecycleCoordinator denyCoordinator = new ResourceLifecycleCoordinator(
                mediatedFileAccess(archive), LIFECYCLE_ACTOR, createDeigmaInspector(), denyPolicy, archive, lexicalIndex);

        ProcessingResult denied = denyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, denied.status());
        assertTrue(searchCoordinator.search("stale", 10).isEmpty());
    }

    @Test
    public void deniedThenReaccepted_unchangedContent_reindexes() throws IOException {
        // Shared archive so snapshot state is consistent across cycles
        InMemoryResourceArchive sharedArchive = new InMemoryResourceArchive();

        PatternResourcePolicy acceptPolicy = allowTextRule();
        PatternResourcePolicy denyPolicy = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "md-only", Arrays.asList("file"), Arrays.asList("**/*.md"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));

        // Step 1: Index a .txt file with accepting policy
        ResourceLifecycleCoordinator acceptCoordinator = new ResourceLifecycleCoordinator(
                mediatedFileAccess(sharedArchive), LIFECYCLE_ACTOR, createDeigmaInspector(),
                acceptPolicy, sharedArchive, lexicalIndex);

        File txtFile = tempFolder.newFile("reaccept.txt");
        writeFile(txtFile, "reacceptable unique content");
        VirtualResourceRef ref = fileRef(txtFile);

        ProcessingResult first = acceptCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, first.status());
        assertFalse(searchCoordinator.search("reacceptable", 10).isEmpty());

        // Step 2: Deny the same ref — lexical entry + archive snapshot removed
        ResourceLifecycleCoordinator denyCoordinator = new ResourceLifecycleCoordinator(
                mediatedFileAccess(sharedArchive), LIFECYCLE_ACTOR, createDeigmaInspector(),
                denyPolicy, sharedArchive, lexicalIndex);

        ProcessingResult denied = denyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, denied.status());
        assertTrue(searchCoordinator.search("reacceptable", 10).isEmpty());

        // Step 3: Re-accept the same unchanged file — should re-index (not UNCHANGED)
        ProcessingResult reindexed = acceptCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, reindexed.status());
        assertFalse(searchCoordinator.search("reacceptable", 10).isEmpty());
    }

    @Test
    public void unchangedContentSkipsReindexing() throws IOException {
        File txtFile = tempFolder.newFile("stable.txt");
        writeFile(txtFile, "Stable content that does not change.");

        VirtualResourceRef ref = fileRef(txtFile);

        ProcessingResult first = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, first.status());

        ProcessingResult second = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.UNCHANGED, second.status());
    }

    /**
     * Documents a guarantee lost in #10 Slice 1 and tracked by #33/#5: before the slice the
     * direct provider path re-read the source on every run, so a rewritten file produced a new
     * digest and was re-indexed. The archive counter caches bronze content without
     * invalidation, so a changed source is now served from the cache within one service
     * instance and the lifecycle reports {@code UNCHANGED}. When invalidation exists, this
     * test must be inverted (expect {@code INDEXED} and the new term to be searchable).
     */
    @Test
    public void knownGap_changedSourceContent_isServedFromCounterCacheUntilInvalidationExists() throws IOException {
        File txtFile = tempFolder.newFile("mutable.txt");
        writeFile(txtFile, "original mutable content");
        VirtualResourceRef ref = fileRef(txtFile);
        assertEquals(ProcessingResult.Status.INDEXED, coordinator.process(ref).status());

        writeFile(txtFile, "replacement mutable content");
        ProcessingResult second = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.UNCHANGED, second.status());
        assertTrue("the stale bytes come from the counter cache", counter.hasCachedContent(ref.uri()));
        assertTrue("stale bronze cache: new content is not visible yet",
                searchCoordinator.search("replacement", 10).isEmpty());
    }

    @Test
    public void extractionWithNoTextBlocks_producesFailedResult() throws IOException {
        File txtFile = tempFolder.newFile("empty-blocks.txt");
        writeFile(txtFile, "content");

        VirtualResourceRef ref = fileRef(txtFile);

        ContentInspector emptyInspector = new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef r, byte[] content, String filenameHint) {
                List<String> blocks = Arrays.asList(null, "", null);
                return InspectionResult.success("text/plain", blocks);
            }
        };

        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        ResourceLifecycleCoordinator coord = new ResourceLifecycleCoordinator(
                mediatedFileAccess(archive), LIFECYCLE_ACTOR, emptyInspector, allowAllFiles(), archive, lexicalIndex);

        ProcessingResult result = coord.process(ref);
        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("No indexable text"));
    }

    @Test
    public void noIndexableTextAfterPreviouslyIndexed_removesStaleSearchEntry() throws IOException {
        File txtFile = tempFolder.newFile("stale-no-text.txt");
        writeFile(txtFile, "stale term before no text");
        VirtualResourceRef ref = fileRef(txtFile);

        ProcessingResult indexed = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, indexed.status());
        assertFalse(searchCoordinator.search("stale", 10).isEmpty());

        ContentInspector emptyInspector = new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef r, byte[] content, String filenameHint) {
                return InspectionResult.success("text/plain", Arrays.asList(null, "", " "));
            }
        };

        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        ResourceLifecycleCoordinator emptyCoordinator = new ResourceLifecycleCoordinator(
                mediatedFileAccess(archive), LIFECYCLE_ACTOR, emptyInspector, allowAllFiles(), archive, lexicalIndex);

        ProcessingResult failed = emptyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.FAILED, failed.status());
        assertTrue(searchCoordinator.search("stale", 10).isEmpty());
    }

    @Test
    public void noTextThenReprocessed_unchangedContent_reindexes() throws IOException {
        InMemoryResourceArchive sharedArchive = new InMemoryResourceArchive();
        PatternResourcePolicy acceptPolicy = allowAllFiles();

        // Step 1: Index with normal inspector
        ResourceLifecycleCoordinator normalCoordinator = new ResourceLifecycleCoordinator(
                mediatedFileAccess(sharedArchive), LIFECYCLE_ACTOR, createDeigmaInspector(),
                acceptPolicy, sharedArchive, lexicalIndex);

        File txtFile = tempFolder.newFile("notext-reindex.txt");
        writeFile(txtFile, "notext reindex unique content");
        VirtualResourceRef ref = fileRef(txtFile);

        ProcessingResult first = normalCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, first.status());
        assertFalse(searchCoordinator.search("notext", 10).isEmpty());

        // Step 2: Keep same file content but use empty inspector, which triggers cleanup
        ContentInspector emptyInspector = new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef r, byte[] content, String filenameHint) {
                return InspectionResult.success("text/plain", Arrays.asList(null, "", " "));
            }
        };
        ResourceLifecycleCoordinator emptyCoordinator = new ResourceLifecycleCoordinator(
                mediatedFileAccess(sharedArchive), LIFECYCLE_ACTOR, emptyInspector,
                acceptPolicy, sharedArchive, lexicalIndex);

        ProcessingResult noText = emptyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.FAILED, noText.status());
        assertTrue(searchCoordinator.search("notext", 10).isEmpty());
        assertNull(sharedArchive.find(ref));

        // Step 3: Re-process same unchanged file with normal inspector — archive snapshot was
        // removed during cleanup, so hasChanged returns true and file is re-indexed
        ProcessingResult reindexed = normalCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, reindexed.status());
        assertFalse(searchCoordinator.search("notext", 10).isEmpty());
    }

    @Test
    public void extractionWithMixedTextAndMetadataBlocks_indexesTextBlocksOnly() throws IOException {
        File txtFile = tempFolder.newFile("mixed-blocks.txt");
        writeFile(txtFile, "content");

        VirtualResourceRef ref = fileRef(txtFile);

        ContentInspector mixedInspector = new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef r, byte[] content, String filenameHint) {
                return InspectionResult.success("text/plain", Arrays.asList(null, "", "kept text", " "));
            }
        };

        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        ResourceLifecycleCoordinator coord = new ResourceLifecycleCoordinator(
                mediatedFileAccess(archive), LIFECYCLE_ACTOR, mixedInspector, allowAllFiles(), archive, lexicalIndex);

        ProcessingResult result = coord.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, result.status());
    }

    @Test
    public void processWithNullRef_producesFailedResult() {
        ProcessingResult result = coordinator.process(null);
        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("must not be null"));
    }

    @Test
    public void processWithUnsupportedScheme_producesFailedResult_fromTheCounter() {
        // The indexing policy accepts any scheme; the counter has only a file: connector,
        // so acquisition fails inside the mediated path and surfaces as FAILED, not as an exception.
        VirtualResourceRef ref = new VirtualResourceRef(
                BookmarkUri.parse("ndv://mainframe/library/program"), VirtualResourceKind.FILE);
        PatternResourcePolicy anyScheme = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "any-scheme", Collections.<String>emptyList(), Arrays.asList("**/*"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));

        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        ResourceLifecycleCoordinator localCoordinator = new ResourceLifecycleCoordinator(
                mediatedFileAccess(archive), LIFECYCLE_ACTOR, createDeigmaInspector(), anyScheme, archive, lexicalIndex);

        ProcessingResult result = localCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertTrue(result.message(), result.message().contains("Acquisition failed"));
    }

    // --- Composition: the mediated counter over a real file connector ---

    /**
     * Builds the archive counter the way a composition point would: Tamias access policy,
     * Holkas acquisition behind {@link AcquisitionPort}, shared snapshot archive.
     * The access policy permits everything; the indexing policy is still exercised per test.
     */
    private static MediatedResourceService mediatedFileAccess(ResourceArchive archive) {
        AcquisitionPort acquisition = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));
        return new MediatedResourceService(permitAll(), acquisition, archive);
    }

    private static ResourceAccessPolicy permitAll() {
        return new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };
    }

    private static PatternResourcePolicy allowTextRule() {
        return new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "allow-txt", Arrays.asList("file"), Arrays.asList("**/*.txt"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));
    }

    private static PatternResourcePolicy allowAllFiles() {
        return new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "allow-all", Arrays.asList("file"), Arrays.asList("**/*"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));
    }

    // --- Adapter wiring: deigma → ContentInspector port ---

    private static ContentInspector createDeigmaInspector() {
        final SimpleContentDetector detector = new SimpleContentDetector();
        final ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new PlainTextExtractor());
        registry.register(new MarkdownTextExtractor());

        return new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef ref, byte[] content, String filenameHint) {
                byte[] prefix = content.length > 64
                        ? Arrays.copyOf(content, 64) : content;
                DetectedContentType detectedType = detector.detect(filenameHint, null, prefix);

                ResourceExtractor extractor = registry.findExtractor(detectedType);
                if (extractor == null) {
                    return InspectionResult.failure(
                            "No extractor for content type: " + detectedType.mimeType());
                }

                ExtractionRequest request = new ExtractionRequest(
                        ref, content, filenameHint, null, detectedType);
                ExtractionResult extraction = extractor.extract(request);
                if (!extraction.isSuccess()) {
                    return InspectionResult.failure("Extraction failed: " + extraction.errorMessage());
                }

                List<String> textBlocks = new ArrayList<String>();
                for (ExtractedBlock block : extraction.document().blocks()) {
                    textBlocks.add(block.text());
                }
                return InspectionResult.success(detectedType.mimeType(), textBlocks);
            }
        };
    }

    private VirtualResourceRef fileRef(File file) {
        BookmarkUri uri = BookmarkUri.parse(file.toURI().toString());
        return new VirtualResourceRef(uri, VirtualResourceKind.FILE);
    }

    private void writeFile(File file, String content) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), UTF_8);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }
}
