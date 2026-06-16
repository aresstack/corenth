package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MediatedHolkasFileSliceTest {

    private static final ActorIdentity ACTOR = new ActorIdentity("user-1", ActorType.HUMAN);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readContent_usesMediatedChalcothecaPathBackedByHolkas() throws Exception {
        File file = temporaryFolder.newFile("note.txt");
        Files.write(file.toPath(), "hello".getBytes(StandardCharsets.UTF_8));
        MediatedResourceService service = mediatedFileService();
        BookmarkUri uri = BookmarkUri.parse(file.toURI().toString());

        MediatedResult<BronzeContent> result = service.readContent(new ResourceAccessRequest(
                ACTOR, uri, ResourceOperation.READ_CONTENT));

        assertTrue(result.isSuccess());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), result.value().content());
        assertTrue(service.hasCachedContent(uri));
    }

    @Test
    public void listChildren_usesMediatedChalcothecaPathBackedByHolkas() throws Exception {
        File dir = temporaryFolder.newFolder("root");
        File child = new File(dir, "a.txt");
        Files.write(child.toPath(), "alpha".getBytes(StandardCharsets.UTF_8));
        MediatedResourceService service = mediatedFileService();
        BookmarkUri uri = BookmarkUri.parse(dir.toURI().toString());

        MediatedResult<BronzeListing> result = service.listChildren(new ResourceAccessRequest(
                ACTOR, uri, ResourceOperation.LIST_CHILDREN));

        assertTrue(result.isSuccess());
        assertEquals(1, result.value().entries().size());
        assertEquals("a.txt", result.value().entries().get(0).name());
        assertEquals(VirtualResourceKind.FILE, result.value().entries().get(0).kind());
        assertTrue(service.hasCachedListing(uri));
    }

    private MediatedResourceService mediatedFileService() {
        HolkasAcquisitionPort acquisitionPort = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));
        return new MediatedResourceService(allowAllPolicy(), acquisitionPort, new InMemoryResourceArchive());
    }

    private ResourceAccessPolicy allowAllPolicy() {
        return new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };
    }
}
