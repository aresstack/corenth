package com.aresstack.corenth.proasteion.katagogion.mediation;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.SearchCoordinator;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalQuery;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex;
import com.aresstack.corenth.proasteion.katagogion.AllowlistToolAdmissionPolicy;
import com.aresstack.corenth.proasteion.katagogion.PluginInstallation;
import com.aresstack.corenth.proasteion.katagogion.PluginRejectionReason;
import com.aresstack.corenth.proasteion.katagogion.SearchOutcome;
import com.aresstack.corenth.proasteion.katagogion.ToolCapabilities;
import com.aresstack.corenth.proasteion.katagogion.ToolCapability;
import com.aresstack.corenth.proasteion.katagogion.ToolFailureReason;
import com.aresstack.corenth.proasteion.katagogion.ToolHost;
import com.aresstack.corenth.proasteion.katagogion.ToolInvocation;
import com.aresstack.corenth.proasteion.katagogion.ToolResult;
import com.aresstack.corenth.proasteion.katagogion.reference.CorenthReferencePlugin;
import com.aresstack.corenth.proasteion.katagogion.reference.LexicalSearchTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the reference search tool end to end: host, admission, capability adapter, the real
 * Acropolis {@link SearchCoordinator} and a real Lucene index in a temporary directory.
 */
class SearchCoordinatorToolPathTest {

    private static final VirtualResourceRef JCL = new VirtualResourceRef(
            BookmarkUri.parse("file:///work/payroll.jcl"), VirtualResourceKind.FILE);
    private static final VirtualResourceRef NOTES = new VirtualResourceRef(
            BookmarkUri.parse("file:///work/notes.txt"), VirtualResourceKind.FILE);

    @TempDir
    Path indexDirectory;

    private LuceneLexicalIndex index;
    private ToolHost host;

    @BeforeEach
    void composeHost() throws IOException {
        index = new LuceneLexicalIndex(new LexicalIndexConfig(indexDirectory));
        index.index(LexicalDocument.builder(JCL).title("Payroll job")
                .fullText("The payroll batch job runs nightly and calls IEBGENER.").build());
        index.index(LexicalDocument.builder(NOTES).title("Notes")
                .fullText("Meeting notes about the cafeteria menu.").build());
        index.commit();

        ToolCapabilities capabilities = ToolCapabilities.builder()
                .lexicalSearch(new SearchCoordinatorLexicalSearch(new SearchCoordinator(index)))
                .build();
        host = new ToolHost(AllowlistToolAdmissionPolicy.builder()
                .allow(CorenthReferencePlugin.ID, EnumSet.allOf(ToolCapability.class))
                .build(), capabilities);
    }

    @AfterEach
    void closeIndex() throws IOException {
        index.close();
    }

    @Test
    void searchToolFindsIndexedResourceThroughTheSearchUseCase() {
        assertTrue(host.install(CorenthReferencePlugin.lexicalSearchOnly()).isInstalled());

        ToolResult result = host.invoke(search("payroll", "5"));

        assertTrue(result.isSuccess(), result.toString());
        assertEquals(1, result.records().size());
        Map<String, String> hit = result.records().get(0);
        assertEquals(JCL.uri().toString(), hit.get("uri"));
        assertEquals("FILE", hit.get("kind"));
        assertEquals("Payroll job", hit.get("title"));
        assertEquals("0", hit.get("chunk"));
        assertTrue(hit.get("excerpt").contains("IEBGENER"));
        assertEquals("1 hit(s)", result.text());
    }

    @Test
    void searchWithoutMatchesSucceedsEmpty() {
        host.install(CorenthReferencePlugin.lexicalSearchOnly());

        ToolResult result = host.invoke(search("mainframe-nonexistent-term", null));

        assertTrue(result.isSuccess());
        assertTrue(result.records().isEmpty());
    }

    @Test
    void invalidMaxHitsIsATypedArgumentFailure() {
        host.install(CorenthReferencePlugin.lexicalSearchOnly());

        assertEquals(ToolFailureReason.INVALID_ARGUMENTS, host.invoke(search("payroll", "0")).failureReason());
        assertEquals(ToolFailureReason.INVALID_ARGUMENTS, host.invoke(search("payroll", "51")).failureReason());
        assertEquals(ToolFailureReason.INVALID_ARGUMENTS, host.invoke(search("payroll", "many")).failureReason());
    }

    @Test
    void fullReferencePluginIsRejectedWhileTheHostOffersNoMediatedReading() {
        PluginInstallation installation = host.install(CorenthReferencePlugin.all());

        assertEquals(PluginRejectionReason.CAPABILITY_UNAVAILABLE, installation.rejectionReason());
        assertTrue(host.tools().isEmpty());
    }

    @Test
    void unreadableIndexBecomesUnavailableInsteadOfAnException() {
        LexicalIndex failingIndex = new LexicalIndex() {
            @Override
            public void index(LexicalDocument document) {
            }

            @Override
            public List<LexicalSearchResult> search(LexicalQuery query) throws IOException {
                throw new IOException("disk gone at /secret/path");
            }

            @Override
            public void remove(VirtualResourceRef resourceRef) {
            }

            @Override
            public void commit() {
            }

            @Override
            public void close() {
            }
        };
        SearchOutcome outcome = new SearchCoordinatorLexicalSearch(new SearchCoordinator(failingIndex)).search("x", 3);

        assertFalse(outcome.isAvailable());
        assertFalse(outcome.unavailableMessage().contains("/secret/path"));

        ToolHost failingHost = new ToolHost(AllowlistToolAdmissionPolicy.builder()
                .allow(CorenthReferencePlugin.ID, EnumSet.of(ToolCapability.LEXICAL_SEARCH)).build(),
                ToolCapabilities.builder()
                        .lexicalSearch(new SearchCoordinatorLexicalSearch(new SearchCoordinator(failingIndex)))
                        .build());
        failingHost.install(CorenthReferencePlugin.lexicalSearchOnly());
        assertEquals(ToolFailureReason.UNAVAILABLE, failingHost.invoke(search("payroll", null)).failureReason());
    }

    private static ToolInvocation search(String query, String maxHits) {
        Map<String, String> arguments = new LinkedHashMap<String, String>();
        arguments.put("query", query);
        if (maxHits != null) {
            arguments.put("max_hits", maxHits);
        }
        return new ToolInvocation(LexicalSearchTool.NAME, arguments);
    }
}
