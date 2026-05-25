package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AcceptanceDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import com.aresstack.corenth.proasteion.emporion.holkas.FileSystemResourceConnector;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration test proving the full walking skeleton path:
 * file: URI → holkas → deigma → tamias → chalcotheca → anagraphai → search result.
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

        // Holkas: file connector
        FileSystemResourceConnector connector = new FileSystemResourceConnector();

        // Deigma: content detection and extraction
        SimpleContentDetector detector = new SimpleContentDetector();
        ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new PlainTextExtractor());
        registry.register(new MarkdownTextExtractor());

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
                connector, detector, registry, policy, archive, lexicalIndex);
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
                new FileSystemResourceConnector(),
                new SimpleContentDetector(),
                createRegistry(),
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

    private VirtualResourceRef fileRef(File file) {
        BookmarkUri uri = BookmarkUri.parse(file.toURI().toString());
        return new VirtualResourceRef(uri, VirtualResourceKind.FILE);
    }

    private ExtractionRegistry createRegistry() {
        ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new PlainTextExtractor());
        registry.register(new MarkdownTextExtractor());
        return registry;
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
