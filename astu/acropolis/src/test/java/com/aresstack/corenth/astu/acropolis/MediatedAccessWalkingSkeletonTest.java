package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionPort;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ContentHasher;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceDigest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import com.aresstack.corenth.proasteion.emporion.holkas.FileSystemResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Smoke test proving the mediated access model works with the existing file:
 * walking skeleton. Exercises:
 * <ul>
 *   <li>MediatedResourceService with a real file-backed AcquisitionPort</li>
 *   <li>READ_CONTENT through the mediated path using the holkas FileSystemResourceConnector</li>
 *   <li>LIST_CHILDREN through the mediated path</li>
 *   <li>Existing walking skeleton path (ResourceLifecycleCoordinator) still compiles and works</li>
 * </ul>
 */
public class MediatedAccessWalkingSkeletonTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void mediatedReadContent_withRealFileConnector() throws IOException {
        File txtFile = tempFolder.newFile("mediated-test.txt");
        writeFile(txtFile, "Mediated access content via holkas internally");

        BookmarkUri uri = BookmarkUri.parse(txtFile.toURI().toString());
        ActorIdentity actor = new ActorIdentity("smoke-user", ActorType.HUMAN);

        ResourceAccessPolicy allowAll = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };

        AcquisitionPort fileAcquisition = createFileAcquisitionPort();
        InMemoryResourceArchive archive = new InMemoryResourceArchive();

        MediatedResourceService service = new MediatedResourceService(allowAll, fileAcquisition, archive);

        ResourceAccessRequest request = new ResourceAccessRequest(
                actor, uri, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> result = service.readContent(request);

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
        ActorIdentity actor = new ActorIdentity("smoke-user", ActorType.HUMAN);

        ResourceAccessPolicy allowAll = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };

        AcquisitionPort fileAcquisition = createFileAcquisitionPort();
        InMemoryResourceArchive archive = new InMemoryResourceArchive();

        MediatedResourceService service = new MediatedResourceService(allowAll, fileAcquisition, archive);

        ResourceAccessRequest request = new ResourceAccessRequest(
                actor, dirUri, ResourceOperation.LIST_CHILDREN);
        MediatedResult<BronzeListing> result = service.listChildren(request);

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
    public void existingWalkingSkeletonCoordinator_stillFunctions() throws IOException {
        // Prove the existing ResourceLifecycleCoordinator can still be instantiated
        // and process a resource — this confirms no API breakage.
        File txtFile = tempFolder.newFile("skeleton-intact.txt");
        writeFile(txtFile, "Walking skeleton still works after mediated model introduction.");

        VirtualResourceRef ref = new VirtualResourceRef(
                BookmarkUri.parse(txtFile.toURI().toString()), VirtualResourceKind.FILE);

        RawResourceProvider provider = createRawResourceProvider();
        ContentInspector inspector = new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef r, byte[] content, String filenameHint) {
                List<String> blocks = new ArrayList<String>();
                blocks.add(new String(content, UTF_8));
                return InspectionResult.success("text/plain", blocks);
            }
        };

        com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy policy =
                new com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy(
                        java.util.Arrays.asList(
                                new com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule(
                                        "all", java.util.Arrays.asList("file"),
                                        java.util.Arrays.asList("**/*"),
                                        java.util.Collections.<String>emptyList(), Long.MAX_VALUE)));

        InMemoryResourceArchive archive = new InMemoryResourceArchive();

        File indexDir = tempFolder.newFolder("lucene-idx");
        com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig indexConfig =
                new com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig(indexDir.toPath());
        com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex lexicalIndex =
                new com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex(indexConfig);

        try {
            ResourceLifecycleCoordinator coordinator = new ResourceLifecycleCoordinator(
                    provider, inspector, policy, archive, lexicalIndex);

            ProcessingResult result = coordinator.process(ref);
            assertEquals(ProcessingResult.Status.INDEXED, result.status());
        } finally {
            lexicalIndex.close();
        }
    }

    // ── Helpers ──

    private AcquisitionPort createFileAcquisitionPort() {
        final FileSystemResourceConnector connector = new FileSystemResourceConnector();
        return new AcquisitionPort() {
            @Override
            public BronzeContent fetchContent(BookmarkUri uri) throws IOException {
                VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
                RawResource raw = connector.fetch(ref);
                byte[] bytes = raw.content().bytes();
                ResourceDigest digest = ContentHasher.digest(bytes);
                return new BronzeContent(uri, bytes, digest, System.currentTimeMillis());
            }

            @Override
            public BronzeListing listChildren(BookmarkUri uri) throws IOException {
                File dir = new File(Paths.get(uri.toURI()).toString());
                File[] files = dir.listFiles();
                List<BronzeListing.Entry> entries = new ArrayList<BronzeListing.Entry>();
                if (files != null) {
                    for (File f : files) {
                        BookmarkUri childUri = BookmarkUri.parse(f.toURI().toString());
                        VirtualResourceKind kind = f.isDirectory()
                                ? VirtualResourceKind.DIRECTORY : VirtualResourceKind.FILE;
                        entries.add(new BronzeListing.Entry(childUri, f.getName(), kind));
                    }
                }
                return new BronzeListing(uri, entries, System.currentTimeMillis());
            }
        };
    }

    private RawResourceProvider createRawResourceProvider() {
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
                return Long.valueOf(java.nio.file.Files.size(
                        java.nio.file.Paths.get(ref.uri().toURI())));
            }
        };
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
