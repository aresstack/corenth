package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentDetector;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import com.aresstack.corenth.proasteion.emporion.holkas.FileSystemResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResource;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration test proving the full walking skeleton path:
 * file: URI → holkas → deigma → tamias → chalcotheca → anagraphai → search result.
 *
 * <p>This test wires outer adapter implementations (holkas, deigma) to the
 * inward-facing ports used by acropolis. The main code never compiles against
 * proasteion packages — only this test composition layer does.
 */
public class WalkingSkeletonIntegrationTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private LuceneLexicalIndex lexicalIndex;
    private ResourceLifecycleCoordinator coordinator;
    private SearchCoordinator searchCoordinator;

    @Before
    public void setUp() throws IOException {
        // Lucene index in temp directory
        File indexDir = tempFolder.newFolder("lucene-index");
        LexicalIndexConfig indexConfig = new LexicalIndexConfig(indexDir.toPath());
        lexicalIndex = new LuceneLexicalIndex(indexConfig);

        // Adapter: holkas file connector → RawResourceProvider port
        RawResourceProvider resourceProvider = createFileResourceProvider();

        // Adapter: deigma detection + extraction → ContentInspector port
        ContentInspector inspector = createDeigmaInspector();

        // Tamias: policy allowing .txt and .md files under 1MB
        IndexingRule textRule = new IndexingRule(
                "file-text-documents",
                Arrays.asList("file"),
                Arrays.asList("**/*.txt", "**/*.md"),
                Arrays.asList("**/.git/**", "**/target/**", "**/build/**"),
                1048576 // 1 MB
        );
        PatternResourcePolicy policy = new PatternResourcePolicy(Arrays.asList(textRule));

        // Chalcotheca: in-memory archive
        InMemoryResourceArchive archive = new InMemoryResourceArchive();

        // Acropolis: coordinator and search
        coordinator = new ResourceLifecycleCoordinator(
                resourceProvider, inspector, policy, archive, lexicalIndex);
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
        // Create a temporary .txt file
        File txtFile = tempFolder.newFile("architecture-notes.txt");
        writeFile(txtFile, "The Corenth architecture uses a walking skeleton approach "
                + "to prove the full pipeline end to end.");

        // Address as file: URI
        VirtualResourceRef ref = fileRef(txtFile);

        // Process through the full pipeline
        ProcessingResult result = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, result.status());

        // Search and verify the result is linked to the original VirtualResourceRef
        List<LexicalSearchResult> results = searchCoordinator.search("architecture", 10);
        assertFalse("Expected at least one search result", results.isEmpty());
        assertEquals(ref, results.get(0).resourceRef());
        assertTrue(results.get(0).excerpt().contains("architecture"));
    }

    @Test
    public void fullPathProcessAndSearch_mdFile() throws IOException {
        // Create a temporary .md file
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
        // Create a .pdf file (not in include patterns)
        File pdfFile = tempFolder.newFile("document.pdf");
        writeFile(pdfFile, "fake pdf content");

        VirtualResourceRef ref = fileRef(pdfFile);
        ProcessingResult result = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("no matching rule"));
    }

    @Test
    public void policyDenies_excludedPath() throws IOException {
        // Create a file under a .git directory
        File gitDir = tempFolder.newFolder(".git");
        File gitFile = new File(gitDir, "config.txt");
        writeFile(gitFile, "git config content");

        VirtualResourceRef ref = fileRef(gitFile);
        ProcessingResult result = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("excluded"));
    }

    @Test
    public void policyDenies_fileTooLarge() throws IOException {
        // Create an indexing rule with a very small maxBytes
        IndexingRule tinyRule = new IndexingRule(
                "tiny-rule",
                Arrays.asList("file"),
                Arrays.asList("**/*.txt"),
                Collections.<String>emptyList(),
                10 // only 10 bytes allowed
        );
        PatternResourcePolicy tinyPolicy = new PatternResourcePolicy(Arrays.asList(tinyRule));

        // Replace coordinator with tiny policy
        ResourceLifecycleCoordinator tinyCoordinator = new ResourceLifecycleCoordinator(
                createFileResourceProvider(),
                createDeigmaInspector(),
                tinyPolicy,
                new InMemoryResourceArchive(),
                lexicalIndex);

        File bigFile = tempFolder.newFile("big.txt");
        writeFile(bigFile, "This content is definitely larger than 10 bytes.");

        VirtualResourceRef ref = fileRef(bigFile);
        ProcessingResult result = tinyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("maxBytes"));
    }

    @Test
    public void policyDenies_fileTooLarge_beforeFetch() throws IOException {
        File bigFile = tempFolder.newFile("big-before-fetch.txt");
        writeFile(bigFile, "This content is definitely larger than 10 bytes.");
        VirtualResourceRef ref = fileRef(bigFile);

        final int[] fetchCalls = new int[1];
        RawResourceProvider countingProvider = new RawResourceProvider() {
            @Override
            public FetchedResource fetch(VirtualResourceRef ignored) {
                fetchCalls[0]++;
                return new FetchedResource(new byte[]{1}, "x.txt", 1);
            }

            @Override
            public Long probeSizeBytes(VirtualResourceRef ignored) {
                return Long.valueOf(1024);
            }
        };

        PatternResourcePolicy tinyPolicy = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "tiny-rule",
                Arrays.asList("file"),
                Arrays.asList("**/*.txt"),
                Collections.<String>emptyList(),
                10
        )));

        ResourceLifecycleCoordinator tinyCoordinator = new ResourceLifecycleCoordinator(
                countingProvider,
                createDeigmaInspector(),
                tinyPolicy,
                new InMemoryResourceArchive(),
                lexicalIndex);

        ProcessingResult result = tinyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("maxBytes"));
        assertEquals(0, fetchCalls[0]);
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
        ResourceLifecycleCoordinator denyCoordinator = new ResourceLifecycleCoordinator(
                createFileResourceProvider(),
                createDeigmaInspector(),
                denyPolicy,
                new InMemoryResourceArchive(),
                lexicalIndex);

        ProcessingResult denied = denyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.DENIED, denied.status());
        assertTrue(searchCoordinator.search("stale", 10).isEmpty());
    }

    @Test
    public void unchangedContentSkipsReindexing() throws IOException {
        File txtFile = tempFolder.newFile("stable.txt");
        writeFile(txtFile, "Stable content that does not change.");

        VirtualResourceRef ref = fileRef(txtFile);

        // First processing should index
        ProcessingResult first = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.INDEXED, first.status());

        // Second processing should detect unchanged
        ProcessingResult second = coordinator.process(ref);
        assertEquals(ProcessingResult.Status.UNCHANGED, second.status());
    }

    @Test
    public void extractionWithNoTextBlocks_producesFailedResult() throws IOException {
        File txtFile = tempFolder.newFile("empty-blocks.txt");
        writeFile(txtFile, "content");

        VirtualResourceRef ref = fileRef(txtFile);

        // Inspector that returns success but with only null/empty text blocks
        ContentInspector emptyInspector = new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef r, byte[] content, String filenameHint) {
                List<String> blocks = Arrays.asList(null, "", null);
                return InspectionResult.success("text/plain", blocks);
            }
        };

        ResourceLifecycleCoordinator coord = new ResourceLifecycleCoordinator(
                createFileResourceProvider(),
                emptyInspector,
                new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                        "allow-all", Arrays.asList("file"), Arrays.asList("**/*"),
                        Collections.<String>emptyList(), Long.MAX_VALUE))),
                new InMemoryResourceArchive(),
                lexicalIndex);

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

        ResourceLifecycleCoordinator emptyCoordinator = new ResourceLifecycleCoordinator(
                createFileResourceProvider(),
                emptyInspector,
                new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                        "allow-all", Arrays.asList("file"), Arrays.asList("**/*"),
                        Collections.<String>emptyList(), Long.MAX_VALUE))),
                new InMemoryResourceArchive(),
                lexicalIndex);

        ProcessingResult failed = emptyCoordinator.process(ref);
        assertEquals(ProcessingResult.Status.FAILED, failed.status());
        assertTrue(searchCoordinator.search("stale", 10).isEmpty());
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

        ResourceLifecycleCoordinator coord = new ResourceLifecycleCoordinator(
                createFileResourceProvider(),
                mixedInspector,
                new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                        "allow-all", Arrays.asList("file"), Arrays.asList("**/*"),
                        Collections.<String>emptyList(), Long.MAX_VALUE))),
                new InMemoryResourceArchive(),
                lexicalIndex);

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
    public void processWithInvalidRef_producesFailedResult() throws IOException {
        File txtFile = tempFolder.newFile("invalid-ref.txt");
        writeFile(txtFile, "content");
        final VirtualResourceRef fileRef = fileRef(txtFile);

        RawResourceProvider invalidProvider = new RawResourceProvider() {
            @Override
            public FetchedResource fetch(VirtualResourceRef ref) {
                throw new IllegalArgumentException("unsupported scheme");
            }
        };

        ResourceLifecycleCoordinator localCoordinator = new ResourceLifecycleCoordinator(
                invalidProvider,
                createDeigmaInspector(),
                new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                        "allow-all", Arrays.asList("file"), Arrays.asList("**/*"),
                        Collections.<String>emptyList(), Long.MAX_VALUE))),
                new InMemoryResourceArchive(),
                lexicalIndex);

        ProcessingResult result = localCoordinator.process(fileRef);
        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("Invalid resource reference"));
    }

    // --- Adapter wiring: bridges proasteion implementations to acropolis ports ---

    private static RawResourceProvider createFileResourceProvider() {
        final FileSystemResourceConnector connector = new FileSystemResourceConnector();
        return new RawResourceProvider() {
            @Override
            public FetchedResource fetch(VirtualResourceRef ref) throws IOException {
                RawResource raw = connector.fetch(ref);
                return new FetchedResource(
                        raw.content().bytes(), raw.filename(), raw.content().sizeBytes());
            }

            @Override
            public Long probeSizeBytes(VirtualResourceRef ref) throws IOException {
                if (ref == null || ref.uri() == null || ref.uri().toURI() == null) {
                    return null;
                }
                Path path = Paths.get(ref.uri().toURI());
                return Long.valueOf(Files.size(path));
            }
        };
    }

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
