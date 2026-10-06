package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionPort;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import com.aresstack.corenth.proasteion.emporion.holkas.DefaultResourceConnectorRegistry;
import com.aresstack.corenth.proasteion.emporion.holkas.FileSystemResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.HolkasAcquisitionPort;

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
 * Smoke test proving the mediated access model works with the real file: connector behind
 * the Holkas acquisition bridge, and that the lifecycle coordinator runs on top of it.
 * Exercises:
 * <ul>
 *   <li>READ_CONTENT through MediatedResourceService backed by HolkasAcquisitionPort</li>
 *   <li>LIST_CHILDREN through the same mediated path</li>
 *   <li>ResourceLifecycleCoordinator processing a resource over the mediated contract</li>
 * </ul>
 */
public class MediatedAccessWalkingSkeletonTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final ActorIdentity ACTOR = new ActorIdentity("smoke-user", ActorType.HUMAN);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void mediatedReadContent_withRealFileConnector() throws IOException {
        File txtFile = tempFolder.newFile("mediated-test.txt");
        writeFile(txtFile, "Mediated access content via holkas internally");

        BookmarkUri uri = BookmarkUri.parse(txtFile.toURI().toString());
        MediatedResourceService service = mediatedFileService(new InMemoryResourceArchive());

        MediatedResult<BronzeContent> result = service.readContent(
                new ResourceAccessRequest(ACTOR, uri, ResourceOperation.READ_CONTENT));

        assertTrue("Mediated read should succeed", result.isSuccess());
        String content = new String(result.value().content(), UTF_8);
        assertEquals("Mediated access content via holkas internally", content);
        assertTrue("Content should be cached after read", service.hasCachedContent(uri));
    }

    @Test
    public void mediatedListChildren_withRealFileConnector() throws IOException {
        File folder = tempFolder.newFolder("listing-test");
        File child1 = new File(folder, "file-a.txt");
        File child2 = new File(folder, "file-b.md");
        writeFile(child1, "content a");
        writeFile(child2, "content b");

        BookmarkUri dirUri = BookmarkUri.parse(folder.toURI().toString());
        MediatedResourceService service = mediatedFileService(new InMemoryResourceArchive());

        MediatedResult<BronzeListing> result = service.listChildren(
                new ResourceAccessRequest(ACTOR, dirUri, ResourceOperation.LIST_CHILDREN));

        assertTrue("Mediated listing should succeed", result.isSuccess());
        BronzeListing listing = result.value();
        assertEquals(2, listing.entries().size());

        List<String> names = new ArrayList<String>();
        for (BronzeListing.Entry e : listing.entries()) {
            names.add(e.name());
        }
        assertTrue(names.contains("file-a.txt"));
        assertTrue(names.contains("file-b.md"));
    }

    @Test
    public void lifecycleCoordinator_runsOverTheMediatedCounter() throws IOException {
        File txtFile = tempFolder.newFile("skeleton-intact.txt");
        writeFile(txtFile, "Walking skeleton runs through the mediated bronze archive counter.");

        VirtualResourceRef ref = new VirtualResourceRef(
                BookmarkUri.parse(txtFile.toURI().toString()), VirtualResourceKind.FILE);

        ContentInspector inspector = new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef r, byte[] content, String filenameHint) {
                List<String> blocks = new ArrayList<String>();
                blocks.add(new String(content, UTF_8));
                return InspectionResult.success("text/plain", blocks);
            }
        };

        PatternResourcePolicy policy = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "all", Arrays.asList("file"), Arrays.asList("**/*"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));

        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        MediatedResourceService counter = mediatedFileService(archive);

        File indexDir = tempFolder.newFolder("lucene-idx");
        LuceneLexicalIndex lexicalIndex = new LuceneLexicalIndex(new LexicalIndexConfig(indexDir.toPath()));
        try {
            ResourceLifecycleCoordinator coordinator = new ResourceLifecycleCoordinator(
                    counter, ACTOR, inspector, policy, archive, lexicalIndex);

            ProcessingResult result = coordinator.process(ref);
            assertEquals(ProcessingResult.Status.INDEXED, result.status());
            assertTrue("the lifecycle must have read through the counter", counter.hasCachedContent(ref.uri()));
        } finally {
            lexicalIndex.close();
        }
    }

    // ── Helpers ──

    private static MediatedResourceService mediatedFileService(InMemoryResourceArchive archive) {
        ResourceAccessPolicy allowAll = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };
        AcquisitionPort fileAcquisition = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));
        return new MediatedResourceService(allowAll, fileAcquisition, archive);
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
