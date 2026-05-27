package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessDecisionType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessReasonCode;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests proving the mediated bronze access model through Chalcotheca/Tamias.
 *
 * <p>Acceptance criteria:
 * <ol>
 *   <li>A caller can list/read a file: resource only through the mediated path.</li>
 *   <li>LIST_CHILDREN can be allowed for HUMAN and denied for BOT.</li>
 *   <li>User-specific denial does NOT delete a global archived snapshot/listing.</li>
 *   <li>Blacklist/tombstone policy can remove archive/index state explicitly.</li>
 *   <li>Unknown/stale BookmarkUri resources can trigger acquisition when Tamias allows.</li>
 *   <li>Existing file walking skeleton behavior remains intact (tested separately).</li>
 * </ol>
 */
public class MediatedResourceServiceTest {

    private static final BookmarkUri FILE_URI = BookmarkUri.parse("file:///tmp/test/hello.txt");
    private static final BookmarkUri DIR_URI = BookmarkUri.parse("file:///tmp/test/");

    private static final ActorIdentity HUMAN_ACTOR = new ActorIdentity("user-1", ActorType.HUMAN);
    private static final ActorIdentity BOT_ACTOR = new ActorIdentity("bot-1", ActorType.BOT);

    private InMemoryResourceArchive archive;
    private StubAcquisitionPort acquisitionPort;

    @Before
    public void setUp() {
        archive = new InMemoryResourceArchive();
        acquisitionPort = new StubAcquisitionPort();
    }

    // ── Acceptance criterion 1: Mediated access path ──

    @Test
    public void readContent_throughMediatedPath_succeeds() {
        ResourceAccessPolicy allowAll = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };

        acquisitionPort.setContent(FILE_URI, "Hello, World!".getBytes());

        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);
        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);

        MediatedResult<BronzeContent> result = service.readContent(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.value());
        assertEquals("Hello, World!", new String(result.value().content()));
    }

    @Test
    public void listChildren_throughMediatedPath_succeeds() {
        ResourceAccessPolicy allowAll = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };

        acquisitionPort.setListing(DIR_URI, Arrays.asList(
                new BronzeListing.Entry(FILE_URI, "hello.txt", VirtualResourceKind.FILE)));

        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);
        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, DIR_URI, ResourceOperation.LIST_CHILDREN);

        MediatedResult<BronzeListing> result = service.listChildren(request);

        assertTrue(result.isSuccess());
        assertEquals(1, result.value().entries().size());
        assertEquals("hello.txt", result.value().entries().get(0).name());
    }

    // ── Acceptance criterion 2: Actor-type differentiation ──

    @Test
    public void listChildren_allowedForHuman_deniedForBot() {
        ResourceAccessPolicy actorPolicy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.actor().actorType() == ActorType.BOT) {
                    return ResourceAccessDecision.deny(
                            AccessReasonCode.BOT_RESTRICTED, "Bots may not list directories");
                }
                return ResourceAccessDecision.allow();
            }
        };

        acquisitionPort.setListing(DIR_URI, Arrays.asList(
                new BronzeListing.Entry(FILE_URI, "hello.txt", VirtualResourceKind.FILE)));

        MediatedResourceService service = new MediatedResourceService(actorPolicy, acquisitionPort, archive);

        // Human can list
        ResourceAccessRequest humanRequest = new ResourceAccessRequest(
                HUMAN_ACTOR, DIR_URI, ResourceOperation.LIST_CHILDREN);
        MediatedResult<BronzeListing> humanResult = service.listChildren(humanRequest);
        assertTrue(humanResult.isSuccess());

        // Bot is denied
        ResourceAccessRequest botRequest = new ResourceAccessRequest(
                BOT_ACTOR, DIR_URI, ResourceOperation.LIST_CHILDREN);
        MediatedResult<BronzeListing> botResult = service.listChildren(botRequest);
        assertFalse(botResult.isSuccess());
        assertTrue(botResult.isDenied());
        assertEquals(AccessReasonCode.BOT_RESTRICTED, botResult.decision().reasonCode());
    }

    // ── Acceptance criterion 3: User-specific denial does NOT delete global state ──

    @Test
    public void userDenial_doesNotDeleteGlobalArchivedState() {
        // First, populate the archive through a human actor
        ResourceAccessPolicy allowHumanDenyBot = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.actor().actorType() == ActorType.BOT) {
                    return ResourceAccessDecision.deny(
                            AccessReasonCode.NOT_VISIBLE_TO_ACTOR, "Not visible to this actor");
                }
                return ResourceAccessDecision.allow();
            }
        };

        acquisitionPort.setContent(FILE_URI, "Important data".getBytes());

        MediatedResourceService service = new MediatedResourceService(allowHumanDenyBot, acquisitionPort, archive);

        // Human reads (populates the cache)
        ResourceAccessRequest humanRead = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> humanResult = service.readContent(humanRead);
        assertTrue(humanResult.isSuccess());

        // Verify content is cached
        assertTrue(service.hasCachedContent(FILE_URI));

        // Bot is denied — but this must NOT delete the global cached state
        ResourceAccessRequest botRead = new ResourceAccessRequest(
                BOT_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> botResult = service.readContent(botRead);
        assertTrue(botResult.isDenied());

        // Global state still exists
        assertTrue(service.hasCachedContent(FILE_URI));

        // Archive snapshot still exists
        VirtualResourceRef ref = new VirtualResourceRef(FILE_URI, VirtualResourceKind.FILE);
        assertNotNull(archive.find(ref));
    }

    // ── Acceptance criterion 4: Blacklist/tombstone removes archive state ──

    @Test
    public void blacklistTombstone_removesArchiveState() {
        ResourceAccessPolicy allowAll = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };

        acquisitionPort.setContent(FILE_URI, "Content to be tombstoned".getBytes());

        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);

        // First, populate
        ResourceAccessRequest readReq = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        service.readContent(readReq);
        assertTrue(service.hasCachedContent(FILE_URI));

        // Now explicitly delete (tombstone) — this is the blacklist/tombstone path
        ResourceAccessRequest deleteReq = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.DELETE_ARCHIVE_ENTRY);
        MediatedResult<Void> deleteResult = service.deleteEntry(deleteReq);
        assertTrue(deleteResult.isSuccess());

        // Archive state is removed
        assertFalse(service.hasCachedContent(FILE_URI));
        VirtualResourceRef ref = new VirtualResourceRef(FILE_URI, VirtualResourceKind.FILE);
        assertNull(archive.find(ref));
    }

    // ── Acceptance criterion 5: Unknown URI triggers acquisition when allowed ──

    @Test
    public void unknownUri_triggersAcquisition_whenAllowed() {
        ResourceAccessPolicy allowAll = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };

        // No pre-cached content; the acquisition port has it
        acquisitionPort.setContent(FILE_URI, "Newly acquired content".getBytes());

        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);

        // Content not yet in cache
        assertFalse(service.hasCachedContent(FILE_URI));

        // Request triggers acquisition
        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> result = service.readContent(request);

        assertTrue(result.isSuccess());
        assertEquals("Newly acquired content", new String(result.value().content()));

        // Now it's in cache
        assertTrue(service.hasCachedContent(FILE_URI));
    }

    @Test
    public void unknownUri_doesNotAcquire_whenDenied() {
        ResourceAccessPolicy denyAll = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.deny(
                        AccessReasonCode.BLACKLISTED, "Resource is blacklisted");
            }
        };

        acquisitionPort.setContent(FILE_URI, "Should not be acquired".getBytes());

        MediatedResourceService service = new MediatedResourceService(denyAll, acquisitionPort, archive);

        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> result = service.readContent(request);

        assertFalse(result.isSuccess());
        assertTrue(result.isDenied());
        assertFalse(service.hasCachedContent(FILE_URI));
    }

    // ── Helper: Stub acquisition port ──

    private static class StubAcquisitionPort implements AcquisitionPort {
        private final java.util.Map<BookmarkUri, byte[]> contentMap =
                new java.util.HashMap<BookmarkUri, byte[]>();
        private final java.util.Map<BookmarkUri, java.util.List<BronzeListing.Entry>> listingMap =
                new java.util.HashMap<BookmarkUri, java.util.List<BronzeListing.Entry>>();

        void setContent(BookmarkUri uri, byte[] content) {
            contentMap.put(uri, content);
        }

        void setListing(BookmarkUri uri, java.util.List<BronzeListing.Entry> entries) {
            listingMap.put(uri, entries);
        }

        @Override
        public BronzeContent fetchContent(BookmarkUri uri) throws IOException {
            byte[] data = contentMap.get(uri);
            if (data == null) {
                throw new IOException("No content available for: " + uri);
            }
            ResourceDigest digest = ContentHasher.digest(data);
            return new BronzeContent(uri, data, digest, System.currentTimeMillis());
        }

        @Override
        public BronzeListing listChildren(BookmarkUri uri) throws IOException {
            java.util.List<BronzeListing.Entry> entries = listingMap.get(uri);
            if (entries == null) {
                throw new IOException("No listing available for: " + uri);
            }
            return new BronzeListing(uri, entries, System.currentTimeMillis());
        }
    }
}
