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
 *   <li>Existing file walking skeleton behavior remains intact.</li>
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
        ResourceAccessPolicy allowAll = allowAllPolicy();

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
        ResourceAccessPolicy allowAll = allowAllPolicy();

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

    // ── Fix 1: Operation validation ──

    @Test
    public void listChildren_rejectsWrongOperation() {
        ResourceAccessPolicy allowAll = allowAllPolicy();
        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);

        // Try to call listChildren with READ_CONTENT operation
        ResourceAccessRequest wrongOp = new ResourceAccessRequest(
                HUMAN_ACTOR, DIR_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeListing> result = service.listChildren(wrongOp);

        assertFalse(result.isSuccess());
        assertTrue(result.isDenied());
    }

    @Test
    public void readContent_rejectsWrongOperation() {
        ResourceAccessPolicy allowAll = allowAllPolicy();
        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);

        // Try to call readContent with LIST_CHILDREN operation
        ResourceAccessRequest wrongOp = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.LIST_CHILDREN);
        MediatedResult<BronzeContent> result = service.readContent(wrongOp);

        assertFalse(result.isSuccess());
        assertTrue(result.isDenied());
    }

    @Test
    public void deleteEntry_rejectsWrongOperation() {
        ResourceAccessPolicy allowAll = allowAllPolicy();
        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);

        // Try to call deleteEntry with READ_CONTENT operation
        ResourceAccessRequest wrongOp = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<Void> result = service.deleteEntry(wrongOp);

        assertFalse(result.isSuccess());
        assertTrue(result.isDenied());
    }

    @Test
    public void publicMethods_returnControlledError_onNullRequest() {
        ResourceAccessPolicy allowAll = allowAllPolicy();
        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);

        MediatedResult<BronzeListing> listResult = service.listChildren(null);
        MediatedResult<BronzeContent> readResult = service.readContent(null);
        MediatedResult<Void> deleteResult = service.deleteEntry(null);

        assertFalse(listResult.isSuccess());
        assertEquals("request must not be null", listResult.errorMessage());
        assertFalse(readResult.isSuccess());
        assertEquals("request must not be null", readResult.errorMessage());
        assertFalse(deleteResult.isSuccess());
        assertEquals("request must not be null", deleteResult.errorMessage());
    }

    // ── Fix 2: Separate FETCH_EXTERNAL authorization ──

    @Test
    public void readContent_deniedAcquisition_whenFetchExternalDenied() {
        // Allow READ_CONTENT but deny FETCH_EXTERNAL
        ResourceAccessPolicy readOnlyPolicy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.operation() == ResourceOperation.FETCH_EXTERNAL) {
                    return ResourceAccessDecision.deny(
                            AccessReasonCode.SOURCE_DENIED, "External fetch not permitted");
                }
                return ResourceAccessDecision.allow();
            }
        };

        acquisitionPort.setContent(FILE_URI, "Should not be acquired".getBytes());
        MediatedResourceService service = new MediatedResourceService(readOnlyPolicy, acquisitionPort, archive);

        // No cached content, and FETCH_EXTERNAL is denied → should not acquire
        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> result = service.readContent(request);

        assertFalse(result.isSuccess());
        assertTrue(result.isDenied());
        assertEquals(AccessReasonCode.SOURCE_DENIED, result.decision().reasonCode());
        assertFalse(service.hasCachedContent(FILE_URI));
    }

    @Test
    public void listChildren_deniedAcquisition_whenFetchExternalDenied() {
        // Allow LIST_CHILDREN but deny FETCH_EXTERNAL
        ResourceAccessPolicy readOnlyPolicy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.operation() == ResourceOperation.FETCH_EXTERNAL) {
                    return ResourceAccessDecision.deny(
                            AccessReasonCode.SOURCE_DENIED, "External fetch not permitted");
                }
                return ResourceAccessDecision.allow();
            }
        };

        acquisitionPort.setListing(DIR_URI, Arrays.asList(
                new BronzeListing.Entry(FILE_URI, "hello.txt", VirtualResourceKind.FILE)));
        MediatedResourceService service = new MediatedResourceService(readOnlyPolicy, acquisitionPort, archive);

        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, DIR_URI, ResourceOperation.LIST_CHILDREN);
        MediatedResult<BronzeListing> result = service.listChildren(request);

        assertFalse(result.isSuccess());
        assertTrue(result.isDenied());
        assertEquals(AccessReasonCode.SOURCE_DENIED, result.decision().reasonCode());
    }

    // ── FETCH_EXTERNAL gate: ALLOW_CACHED_ONLY must block acquisition ──

    @Test
    public void readContent_fetchExternalCachedOnly_doesNotAcquire() {
        // Allow READ_CONTENT normally, but FETCH_EXTERNAL returns ALLOW_CACHED_ONLY
        ResourceAccessPolicy policy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.operation() == ResourceOperation.FETCH_EXTERNAL) {
                    return ResourceAccessDecision.cachedOnly("Only cached allowed for fetch");
                }
                return ResourceAccessDecision.allow();
            }
        };

        CallTrackingAcquisitionPort trackingPort = new CallTrackingAcquisitionPort();
        trackingPort.setContent(FILE_URI, "Should not be fetched".getBytes());

        MediatedResourceService service = new MediatedResourceService(policy, trackingPort, archive);

        // No cached content, FETCH_EXTERNAL responds ALLOW_CACHED_ONLY → must not acquire
        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> result = service.readContent(request);

        assertFalse(result.isSuccess());
        assertEquals(AccessDecisionType.ALLOW_CACHED_ONLY, result.decision().type());
        assertEquals(AccessReasonCode.CACHE_ONLY_ALLOWED, result.decision().reasonCode());
        assertFalse(trackingPort.wasFetchContentCalled());
    }

    @Test
    public void listChildren_fetchExternalCachedOnly_doesNotAcquire() {
        // Allow LIST_CHILDREN normally, but FETCH_EXTERNAL returns ALLOW_CACHED_ONLY
        ResourceAccessPolicy policy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.operation() == ResourceOperation.FETCH_EXTERNAL) {
                    return ResourceAccessDecision.cachedOnly("Only cached allowed for fetch");
                }
                return ResourceAccessDecision.allow();
            }
        };

        CallTrackingAcquisitionPort trackingPort = new CallTrackingAcquisitionPort();
        trackingPort.setListing(DIR_URI, Arrays.asList(
                new BronzeListing.Entry(FILE_URI, "hello.txt", VirtualResourceKind.FILE)));

        MediatedResourceService service = new MediatedResourceService(policy, trackingPort, archive);

        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, DIR_URI, ResourceOperation.LIST_CHILDREN);
        MediatedResult<BronzeListing> result = service.listChildren(request);

        assertFalse(result.isSuccess());
        assertEquals(AccessDecisionType.ALLOW_CACHED_ONLY, result.decision().type());
        assertEquals(AccessReasonCode.CACHE_ONLY_ALLOWED, result.decision().reasonCode());
        assertFalse(trackingPort.wasListChildrenCalled());
    }

    @Test
    public void readContent_fetchExternalRequireAuth_preservesDecisionType() {
        ResourceAccessPolicy policy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.operation() == ResourceOperation.FETCH_EXTERNAL) {
                    return new ResourceAccessDecision(
                            AccessDecisionType.REQUIRE_AUTH,
                            AccessReasonCode.SOURCE_AUTH_REQUIRED,
                            "Source auth required");
                }
                return ResourceAccessDecision.allow();
            }
        };

        CallTrackingAcquisitionPort trackingPort = new CallTrackingAcquisitionPort();
        trackingPort.setContent(FILE_URI, "Should not be fetched".getBytes());
        MediatedResourceService service = new MediatedResourceService(policy, trackingPort, archive);

        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> result = service.readContent(request);

        assertFalse(result.isSuccess());
        assertEquals(AccessDecisionType.REQUIRE_AUTH, result.decision().type());
        assertEquals(AccessReasonCode.SOURCE_AUTH_REQUIRED, result.decision().reasonCode());
        assertFalse(trackingPort.wasFetchContentCalled());
    }

    @Test
    public void listChildren_fetchExternalRequireSourceCheck_preservesDecisionType() {
        ResourceAccessPolicy policy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.operation() == ResourceOperation.FETCH_EXTERNAL) {
                    return new ResourceAccessDecision(
                            AccessDecisionType.REQUIRE_SOURCE_CHECK,
                            AccessReasonCode.SOURCE_DENIED,
                            "Source check required");
                }
                return ResourceAccessDecision.allow();
            }
        };

        CallTrackingAcquisitionPort trackingPort = new CallTrackingAcquisitionPort();
        trackingPort.setListing(DIR_URI, Arrays.asList(
                new BronzeListing.Entry(FILE_URI, "hello.txt", VirtualResourceKind.FILE)));
        MediatedResourceService service = new MediatedResourceService(policy, trackingPort, archive);

        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, DIR_URI, ResourceOperation.LIST_CHILDREN);
        MediatedResult<BronzeListing> result = service.listChildren(request);

        assertFalse(result.isSuccess());
        assertEquals(AccessDecisionType.REQUIRE_SOURCE_CHECK, result.decision().type());
        assertEquals(AccessReasonCode.SOURCE_DENIED, result.decision().reasonCode());
        assertFalse(trackingPort.wasListChildrenCalled());
    }

    // ── Fix 3: ALLOW_CACHED_ONLY semantics ──

    @Test
    public void readContent_allowCachedOnly_returnsCached() {
        ResourceAccessPolicy cachedOnlyPolicy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.cachedOnly("Only cached content allowed");
            }
        };

        MediatedResourceService service = new MediatedResourceService(cachedOnlyPolicy, acquisitionPort, archive);

        // Pre-populate cache
        BronzeContent preloaded = new BronzeContent(FILE_URI, "cached data".getBytes(),
                ContentHasher.digest("cached data".getBytes()), System.currentTimeMillis());
        service.storeBronzeContent(preloaded);

        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> result = service.readContent(request);

        assertTrue(result.isSuccess());
        assertEquals("cached data", new String(result.value().content()));
    }

    @Test
    public void readContent_allowCachedOnly_deniesWhenNoCacheExists() {
        ResourceAccessPolicy cachedOnlyPolicy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.cachedOnly("Only cached content allowed");
            }
        };

        acquisitionPort.setContent(FILE_URI, "Should not be fetched".getBytes());
        MediatedResourceService service = new MediatedResourceService(cachedOnlyPolicy, acquisitionPort, archive);

        ResourceAccessRequest request = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.READ_CONTENT);
        MediatedResult<BronzeContent> result = service.readContent(request);

        // Should deny because no cache and external fetch not permitted
        assertFalse(result.isSuccess());
        assertTrue(result.isDenied());
        assertEquals(AccessReasonCode.CACHE_ONLY_ALLOWED, result.decision().reasonCode());
        assertFalse(service.hasCachedContent(FILE_URI));
    }

    // ── Acceptance criterion 2: Actor-type differentiation ──

    @Test
    public void listChildren_allowedForHuman_deniedForBot() {
        ResourceAccessPolicy actorPolicy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.actor().actorType() == ActorType.BOT
                        && request.operation() == ResourceOperation.LIST_CHILDREN) {
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
        // Allow human (including FETCH_EXTERNAL), deny bot for READ_CONTENT
        ResourceAccessPolicy allowHumanDenyBot = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.actor().actorType() == ActorType.BOT
                        && request.operation() == ResourceOperation.READ_CONTENT) {
                    return ResourceAccessDecision.deny(
                            AccessReasonCode.NOT_VISIBLE_TO_ACTOR, "Not visible to this actor");
                }
                return ResourceAccessDecision.allow();
            }
        };

        acquisitionPort.setContent(FILE_URI, "Important data".getBytes());

        MediatedResourceService service = new MediatedResourceService(allowHumanDenyBot, acquisitionPort, archive);

        // Human reads (populates the cache via acquisition)
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
    }

    // ── Acceptance criterion 4: Blacklist/tombstone removes archive state ──

    @Test
    public void blacklistTombstone_removesArchiveState() {
        ResourceAccessPolicy allowAll = allowAllPolicy();

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
    }

    // ── Acceptance criterion 5: Unknown URI triggers acquisition when allowed ──

    @Test
    public void unknownUri_triggersAcquisition_whenAllowed() {
        ResourceAccessPolicy allowAll = allowAllPolicy();

        // No pre-cached content; the acquisition port has it
        acquisitionPort.setContent(FILE_URI, "Newly acquired content".getBytes());

        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);

        // Content not yet in cache
        assertFalse(service.hasCachedContent(FILE_URI));

        // Request triggers acquisition (both READ_CONTENT and FETCH_EXTERNAL allowed)
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

    // ── Fix 4: Defensive copy verification ──

    @Test
    public void bronzeContent_defensiveCopy_preventsExternalMutation() {
        byte[] original = "immutable content".getBytes();
        BronzeContent content = new BronzeContent(FILE_URI, original,
                ContentHasher.digest(original), System.currentTimeMillis());

        // Mutate the original array
        original[0] = 'X';

        // Content should be unchanged (defensive copy on construction)
        assertNotEquals('X', content.content()[0]);
        assertEquals('i', content.content()[0]);

        // Mutate the returned array
        byte[] returned = content.content();
        returned[0] = 'Y';

        // Content should still be unchanged (defensive copy on access)
        assertEquals('i', content.content()[0]);
    }

    // ── Fix 5: Delete is type-agnostic (uses URI not hardcoded FILE kind) ──

    @Test
    public void deleteEntry_removesArchiveState_forDirectoryResource() {
        ResourceAccessPolicy allowAll = allowAllPolicy();

        // Store a directory-type snapshot in archive
        VirtualResourceRef dirRef = new VirtualResourceRef(DIR_URI, VirtualResourceKind.DIRECTORY);
        ResourceDigest digest = ContentHasher.digest("dir-content".getBytes());
        archive.store(new ResourceSnapshot(dirRef, digest, System.currentTimeMillis()));

        assertNotNull(archive.findByUri(DIR_URI));

        MediatedResourceService service = new MediatedResourceService(allowAll, acquisitionPort, archive);

        ResourceAccessRequest deleteReq = new ResourceAccessRequest(
                HUMAN_ACTOR, DIR_URI, ResourceOperation.DELETE_ARCHIVE_ENTRY);
        MediatedResult<Void> result = service.deleteEntry(deleteReq);
        assertTrue(result.isSuccess());

        // Archive state removed by URI regardless of kind
        assertNull(archive.findByUri(DIR_URI));
    }

    @Test
    public void deleteEntry_cachedOnlyDecision_doesNotDeleteArchiveState() {
        ResourceAccessPolicy cachedOnlyPolicy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.cachedOnly("Delete not allowed");
            }
        };

        MediatedResourceService service = new MediatedResourceService(cachedOnlyPolicy, acquisitionPort, archive);
        BronzeContent preloaded = new BronzeContent(FILE_URI, "cached".getBytes(),
                ContentHasher.digest("cached".getBytes()), System.currentTimeMillis());
        service.storeBronzeContent(preloaded);

        VirtualResourceRef fileRef = new VirtualResourceRef(FILE_URI, VirtualResourceKind.FILE);
        ResourceDigest digest = ContentHasher.digest("cached".getBytes());
        archive.store(new ResourceSnapshot(fileRef, digest, System.currentTimeMillis()));

        ResourceAccessRequest deleteReq = new ResourceAccessRequest(
                HUMAN_ACTOR, FILE_URI, ResourceOperation.DELETE_ARCHIVE_ENTRY);
        MediatedResult<Void> result = service.deleteEntry(deleteReq);

        assertFalse(result.isSuccess());
        assertEquals(AccessDecisionType.ALLOW_CACHED_ONLY, result.decision().type());
        assertTrue(service.hasCachedContent(FILE_URI));
        assertNotNull(archive.findByUri(FILE_URI));
    }

    @Test
    public void bronzeContent_rejectsNullDigest() {
        try {
            new BronzeContent(FILE_URI, "x".getBytes(), null, System.currentTimeMillis());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("digest must not be null", e.getMessage());
        }
    }

    @Test
    public void bronzeListingEntry_rejectsNullKind() {
        try {
            new BronzeListing.Entry(FILE_URI, "hello.txt", null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("kind must not be null", e.getMessage());
        }
    }

    // ── Helper: allow-all policy ──

    private static ResourceAccessPolicy allowAllPolicy() {
        return new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                return ResourceAccessDecision.allow();
            }
        };
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

    // ── Helper: Call-tracking acquisition port ──

    private static class CallTrackingAcquisitionPort implements AcquisitionPort {
        private final java.util.Map<BookmarkUri, byte[]> contentMap =
                new java.util.HashMap<BookmarkUri, byte[]>();
        private final java.util.Map<BookmarkUri, java.util.List<BronzeListing.Entry>> listingMap =
                new java.util.HashMap<BookmarkUri, java.util.List<BronzeListing.Entry>>();
        private boolean fetchContentCalled = false;
        private boolean listChildrenCalled = false;

        void setContent(BookmarkUri uri, byte[] content) {
            contentMap.put(uri, content);
        }

        void setListing(BookmarkUri uri, java.util.List<BronzeListing.Entry> entries) {
            listingMap.put(uri, entries);
        }

        boolean wasFetchContentCalled() { return fetchContentCalled; }
        boolean wasListChildrenCalled() { return listChildrenCalled; }

        @Override
        public BronzeContent fetchContent(BookmarkUri uri) throws IOException {
            fetchContentCalled = true;
            byte[] data = contentMap.get(uri);
            if (data == null) {
                throw new IOException("No content available for: " + uri);
            }
            ResourceDigest digest = ContentHasher.digest(data);
            return new BronzeContent(uri, data, digest, System.currentTimeMillis());
        }

        @Override
        public BronzeListing listChildren(BookmarkUri uri) throws IOException {
            listChildrenCalled = true;
            java.util.List<BronzeListing.Entry> entries = listingMap.get(uri);
            if (entries == null) {
                throw new IOException("No listing available for: " + uri);
            }
            return new BronzeListing(uri, entries, System.currentTimeMillis());
        }
    }
}
