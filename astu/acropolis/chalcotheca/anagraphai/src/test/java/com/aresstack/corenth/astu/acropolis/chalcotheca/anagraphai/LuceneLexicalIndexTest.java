package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LuceneLexicalIndexTest {

    @TempDir
    Path tempDir;

    private LuceneLexicalIndex index;

    @BeforeEach
    void setUp() throws IOException {
        LexicalIndexConfig config = new LexicalIndexConfig(tempDir.resolve("index"));
        index = new LuceneLexicalIndex(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (index != null) {
            index.close();
        }
    }

    // ── Indexing and basic search ────────────────────────────────────────────────

    @Test
    void indexAndSearchSingleDocument() throws IOException {
        VirtualResourceRef ref = fileRef("file:///docs/readme.txt");
        LexicalDocument doc = LexicalDocument.builder(ref)
                .title("README")
                .contentType("text/plain")
                .fullText("This is a guide for configuring the application server.")
                .build();

        index.index(doc);
        index.commit();

        List<LexicalSearchResult> results = index.search(new LexicalQuery("configuring application"));
        assertFalse(results.isEmpty());
        assertEquals(ref, results.get(0).resourceRef());
        assertEquals("README", results.get(0).title());
        assertEquals("text/plain", results.get(0).contentType());
        assertTrue(results.get(0).score() > 0);
    }

    @Test
    void indexMultipleChunks() throws IOException {
        VirtualResourceRef ref = fileRef("file:///docs/manual.txt");
        LexicalDocument doc = LexicalDocument.builder(ref)
                .title("Manual")
                .addChunk(new LexicalChunk(0, "Chapter one introduces the basics of the framework."))
                .addChunk(new LexicalChunk(1, "Chapter two covers advanced configuration and deployment."))
                .build();

        index.index(doc);
        index.commit();

        List<LexicalSearchResult> results = index.search(new LexicalQuery("deployment"));
        assertFalse(results.isEmpty());
        assertEquals(1, results.get(0).chunkIndex());
    }

    // ── No-result behavior ──────────────────────────────────────────────────────

    @Test
    void searchEmptyIndexReturnsEmptyList() throws IOException {
        index.commit();
        List<LexicalSearchResult> results = index.search(new LexicalQuery("anything"));
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchWithNoMatchReturnsEmptyList() throws IOException {
        VirtualResourceRef ref = fileRef("file:///docs/file.txt");
        LexicalDocument doc = LexicalDocument.builder(ref)
                .fullText("Apples and oranges in the garden.")
                .build();
        index.index(doc);
        index.commit();

        List<LexicalSearchResult> results = index.search(new LexicalQuery("quantum physics"));
        assertTrue(results.isEmpty());
    }

    // ── Explicit commit model ───────────────────────────────────────────────────

    @Test
    void uncommittedChangesNotVisibleToSearch() throws IOException {
        VirtualResourceRef ref = fileRef("file:///docs/uncommitted.txt");
        index.index(LexicalDocument.builder(ref)
                .fullText("Content that has not been committed yet.")
                .build());
        // Initial commit to create the index so search can open a reader
        index.commit();

        // Now add a new document without committing
        VirtualResourceRef ref2 = fileRef("file:///docs/uncommitted2.txt");
        index.index(LexicalDocument.builder(ref2)
                .fullText("This invisible text was added after the last commit.")
                .build());

        // Search should NOT see the uncommitted document
        List<LexicalSearchResult> results = index.search(new LexicalQuery("invisible"));
        assertTrue(results.isEmpty());

        // After commit, it should be visible
        index.commit();
        results = index.search(new LexicalQuery("invisible"));
        assertFalse(results.isEmpty());
    }

    // ── Update semantics ────────────────────────────────────────────────────────

    @Test
    void reindexReplacesExistingDocument() throws IOException {
        VirtualResourceRef ref = fileRef("file:///docs/changing.txt");

        LexicalDocument v1 = LexicalDocument.builder(ref)
                .fullText("Original content about databases.")
                .build();
        index.index(v1);
        index.commit();

        LexicalDocument v2 = LexicalDocument.builder(ref)
                .fullText("Replaced content about networking.")
                .build();
        index.index(v2);
        index.commit();

        // Old content should not be found
        List<LexicalSearchResult> oldResults = index.search(new LexicalQuery("databases"));
        assertTrue(oldResults.isEmpty());

        // New content should be found
        List<LexicalSearchResult> newResults = index.search(new LexicalQuery("networking"));
        assertFalse(newResults.isEmpty());
        assertEquals(ref, newResults.get(0).resourceRef());
    }

    // ── Remove ──────────────────────────────────────────────────────────────────

    @Test
    void removeDeletesDocumentFromIndex() throws IOException {
        VirtualResourceRef ref = fileRef("file:///docs/removable.txt");
        LexicalDocument doc = LexicalDocument.builder(ref)
                .fullText("Temporary content for removal test.")
                .build();
        index.index(doc);
        index.commit();

        // Verify it exists
        assertFalse(index.search(new LexicalQuery("removal test")).isEmpty());

        // Remove and verify gone
        index.remove(ref);
        index.commit();
        assertTrue(index.search(new LexicalQuery("removal test")).isEmpty());
    }

    // ── Resource identity: URI + kind ───────────────────────────────────────────

    @Test
    void sameUriDifferentKindDoNotCollide() throws IOException {
        BookmarkUri uri = BookmarkUri.parse("file:///data/items");
        VirtualResourceRef fileRef = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
        VirtualResourceRef dirRef = new VirtualResourceRef(uri, VirtualResourceKind.DIRECTORY);

        index.index(LexicalDocument.builder(fileRef)
                .fullText("File content about architecture.")
                .build());
        index.index(LexicalDocument.builder(dirRef)
                .fullText("Directory listing of modules.")
                .build());
        index.commit();

        // Both should be searchable
        List<LexicalSearchResult> archResults = index.search(new LexicalQuery("architecture"));
        assertFalse(archResults.isEmpty());
        assertEquals(VirtualResourceKind.FILE, archResults.get(0).resourceRef().kind());

        List<LexicalSearchResult> dirResults = index.search(new LexicalQuery("directory listing modules"));
        assertFalse(dirResults.isEmpty());
        assertEquals(VirtualResourceKind.DIRECTORY, dirResults.get(0).resourceRef().kind());
    }

    @Test
    void removeBySameUriDifferentKindDoesNotAffectOther() throws IOException {
        BookmarkUri uri = BookmarkUri.parse("file:///data/shared");
        VirtualResourceRef fileRef = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
        VirtualResourceRef dirRef = new VirtualResourceRef(uri, VirtualResourceKind.DIRECTORY);

        index.index(LexicalDocument.builder(fileRef)
                .fullText("Shared file content about protocols.")
                .build());
        index.index(LexicalDocument.builder(dirRef)
                .fullText("Shared directory content about services.")
                .build());
        index.commit();

        // Remove only the FILE variant
        index.remove(fileRef);
        index.commit();

        // FILE content gone
        List<LexicalSearchResult> fileResults = index.search(new LexicalQuery("protocols"));
        assertTrue(fileResults.isEmpty());

        // DIRECTORY content still present
        List<LexicalSearchResult> dirResults = index.search(new LexicalQuery("services"));
        assertFalse(dirResults.isEmpty());
        assertEquals(VirtualResourceKind.DIRECTORY, dirResults.get(0).resourceRef().kind());
    }

    // ── Multiple documents ──────────────────────────────────────────────────────

    @Test
    void searchAcrossMultipleDocuments() throws IOException {
        VirtualResourceRef ref1 = fileRef("file:///docs/alpha.txt");
        VirtualResourceRef ref2 = fileRef("file:///docs/beta.txt");

        index.index(LexicalDocument.builder(ref1)
                .title("Alpha")
                .fullText("Java programming language fundamentals and best practices.")
                .build());
        index.index(LexicalDocument.builder(ref2)
                .title("Beta")
                .fullText("Python scripting for data analysis and automation.")
                .build());
        index.commit();

        List<LexicalSearchResult> javaResults = index.search(new LexicalQuery("Java programming"));
        assertFalse(javaResults.isEmpty());
        assertEquals(ref1, javaResults.get(0).resourceRef());

        List<LexicalSearchResult> pythonResults = index.search(new LexicalQuery("Python scripting"));
        assertFalse(pythonResults.isEmpty());
        assertEquals(ref2, pythonResults.get(0).resourceRef());
    }

    @Test
    void maxResultsLimitsOutput() throws IOException {
        for (int i = 0; i < 5; i++) {
            VirtualResourceRef ref = fileRef("file:///docs/doc" + i + ".txt");
            index.index(LexicalDocument.builder(ref)
                    .fullText("Common keyword appears in document number " + i + ".")
                    .build());
        }
        index.commit();

        List<LexicalSearchResult> results = index.search(new LexicalQuery("keyword", 2));
        assertTrue(results.size() <= 2);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    @Test
    void closeAndReopenIndex() throws IOException {
        VirtualResourceRef ref = fileRef("file:///docs/persistent.txt");
        index.index(LexicalDocument.builder(ref)
                .fullText("Persistent content survives close and reopen.")
                .build());
        index.commit();
        index.close();

        // Reopen
        LexicalIndexConfig config = new LexicalIndexConfig(tempDir.resolve("index"));
        index = new LuceneLexicalIndex(config);

        List<LexicalSearchResult> results = index.search(new LexicalQuery("persistent survives"));
        assertFalse(results.isEmpty());
    }

    // ── Non-standard scheme support ─────────────────────────────────────────────

    @Test
    void indexAndSearchNonStandardScheme() throws IOException {
        BookmarkUri uri = BookmarkUri.parse("ndv://mainframe/system/report.cgp");
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);

        LexicalDocument doc = LexicalDocument.builder(ref)
                .title("Mainframe Report")
                .fullText("Monthly batch processing summary for financial transactions.")
                .build();
        index.index(doc);
        index.commit();

        List<LexicalSearchResult> results = index.search(new LexicalQuery("financial transactions"));
        assertFalse(results.isEmpty());
        assertEquals(ref, results.get(0).resourceRef());
    }

    // ── Model validation ────────────────────────────────────────────────────────

    @Test
    void lexicalQueryValidation() {
        assertThrows(IllegalArgumentException.class, () -> new LexicalQuery(null));
        assertThrows(IllegalArgumentException.class, () -> new LexicalQuery(""));
        assertThrows(IllegalArgumentException.class, () -> new LexicalQuery("   "));
        assertThrows(IllegalArgumentException.class, () -> new LexicalQuery("valid", 0));
    }

    @Test
    void lexicalChunkValidation() {
        assertThrows(IllegalArgumentException.class, () -> new LexicalChunk(-1, "text"));
        assertThrows(IllegalArgumentException.class, () -> new LexicalChunk(0, null));
    }

    @Test
    void lexicalDocumentRequiresChunks() {
        VirtualResourceRef ref = fileRef("file:///docs/empty.txt");
        assertThrows(IllegalStateException.class, () ->
                LexicalDocument.builder(ref).build());
    }

    @Test
    void lexicalIndexConfigValidation() {
        assertThrows(IllegalArgumentException.class, () -> new LexicalIndexConfig(null));
    }

    @Test
    void lexicalSearchResultValidation() {
        VirtualResourceRef ref = fileRef("file:///docs/test.txt");
        assertThrows(IllegalArgumentException.class, () ->
                new LexicalSearchResult(null, 1.0f, 0, "text", null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new LexicalSearchResult(ref, 1.0f, -1, "text", null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new LexicalSearchResult(ref, 1.0f, 0, null, null, null));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static VirtualResourceRef fileRef(String uriString) {
        BookmarkUri uri = BookmarkUri.parse(uriString);
        return new VirtualResourceRef(uri, VirtualResourceKind.FILE);
    }
}
