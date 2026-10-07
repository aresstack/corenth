package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessReasonCode;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Payload refresh, metadata reads and invalidation of the archive counter (#10 Slice 5).
 * Runs against a fake acquisition port; it proves the counter's mediation, not a connector.
 */
public class MediatedPayloadRefreshTest {

    private static final ActorIdentity ACTOR = new ActorIdentity("indexer", ActorType.SERVICE);
    private static final BookmarkUri DOC = BookmarkUri.parse("file:///virtual/doc.txt");

    @Test
    public void refreshPermitted_reacquiresAndReplacesTheCachedPayload() {
        FakePort port = new FakePort();
        MediatedResourceService counter = counter(allowing(), port);
        port.text = "first";
        assertEquals("first", text(counter.refreshContent(read())));

        port.text = "second";
        assertEquals("second", text(counter.refreshContent(read())));
        assertEquals("second", text(counter.readContent(read())));
        assertEquals(2, port.fetches);
    }

    @Test
    public void refreshNotPermitted_servesTheCachedPayload_withoutSourceContact() {
        FakePort port = new FakePort();
        Policy policy = allowing();
        policy.decisions.put(ResourceOperation.REFRESH_EXTERNAL, ResourceAccessDecision.cachedOnly("no refresh"));
        MediatedResourceService counter = counter(policy, port);
        port.text = "first";
        assertEquals("first", text(counter.refreshContent(read())));

        port.text = "second";
        assertEquals("first", text(counter.refreshContent(read())));
        assertEquals(1, port.fetches);
    }

    @Test
    public void refresh_isGatedByReadContent_beforeAnySourceContact() {
        FakePort port = new FakePort();
        Policy policy = allowing();
        policy.decisions.put(ResourceOperation.READ_CONTENT,
                ResourceAccessDecision.deny(AccessReasonCode.BLACKLISTED, "blacklisted"));
        MediatedResult<BronzeContent> result = counter(policy, port).refreshContent(read());

        assertFalse(result.isSuccess());
        assertEquals(AccessReasonCode.BLACKLISTED, result.decision().reasonCode());
        assertEquals(0, port.fetches);
        assertTrue(policy.seen.contains(ResourceOperation.READ_CONTENT));
        assertFalse(policy.seen.contains(ResourceOperation.REFRESH_EXTERNAL));
    }

    @Test
    public void failedRefresh_keepsThePreviousPayload_andReturnsTheTypedAbsence() {
        FakePort port = new FakePort();
        MediatedResourceService counter = counter(allowing(), port);
        port.text = "cached";
        counter.refreshContent(read());

        port.absent = true;
        MediatedResult<BronzeContent> refreshed = counter.refreshContent(read());

        assertEquals(MediatedResult.Failure.SOURCE_ABSENT, refreshed.failure());
        assertNull("stale bytes are never returned for a failed refresh", refreshed.value());
        assertTrue(counter.hasCachedContent(DOC));
    }

    @Test
    public void invalidatePayload_dropsContentListingAndMetadata() {
        FakePort port = new FakePort();
        port.metadata = true;
        MediatedResourceService counter = counter(allowing(), port);
        counter.refreshContent(read());
        counter.readMetadata(metadata());
        counter.storeBronzeListing(new BronzeListing(DOC, Collections.<BronzeListing.Entry>emptyList(), 1L));

        counter.invalidatePayload(DOC);

        assertFalse(counter.hasCachedContent(DOC));
        assertFalse(counter.hasCachedListing(DOC));
    }

    @Test
    public void readMetadata_withoutMetadataSupport_isUnavailable_withoutAccessPreparation() {
        FakePort port = new FakePort();
        final int[] preparations = {0};
        AcquisitionAccessPort preparation = new AcquisitionAccessPort() {
            @Override
            public AcquisitionAccess prepare(AcquisitionAccessRequest request) {
                preparations[0]++;
                return AcquisitionAccess.notRequired();
            }
        };
        MediatedResourceService counter = new MediatedResourceService(
                allowing(), port, new InMemoryResourceArchive(), preparation);

        MediatedResult<BronzeMetadata> result = counter.readMetadata(metadata());

        assertEquals(MediatedResult.Failure.METADATA_UNAVAILABLE, result.failure());
        assertEquals(0, preparations[0]);
    }

    @Test
    public void readMetadata_returnsTheSourceSize_andMapsAbsence() {
        FakePort port = new FakePort();
        port.metadata = true;
        port.text = "12345";
        MediatedResourceService counter = counter(allowing(), port);

        assertEquals(5L, counter.readMetadata(metadata()).value().sizeBytes());
        assertEquals(0, port.fetches);

        port.absent = true;
        assertEquals(MediatedResult.Failure.SOURCE_ABSENT, counter.readMetadata(metadata()).failure());
    }

    @Test
    public void readMetadata_isGatedByFetchExternal() {
        FakePort port = new FakePort();
        port.metadata = true;
        Policy policy = allowing();
        policy.decisions.put(ResourceOperation.FETCH_EXTERNAL,
                ResourceAccessDecision.deny(AccessReasonCode.SOURCE_DENIED, "offline"));

        MediatedResult<BronzeMetadata> result = counter(policy, port).readMetadata(metadata());

        assertFalse(result.isSuccess());
        assertEquals(AccessReasonCode.SOURCE_DENIED, result.decision().reasonCode());
    }

    @Test
    public void refreshAndMetadata_rejectMismatchedOperations() {
        MediatedResourceService counter = counter(allowing(), new FakePort());
        assertEquals(AccessReasonCode.INVALID_OPERATION, counter.refreshContent(metadata()).decision().reasonCode());
        assertEquals(AccessReasonCode.INVALID_OPERATION, counter.readMetadata(read()).decision().reasonCode());
    }

    private static MediatedResourceService counter(ResourceAccessPolicy policy, AcquisitionPort port) {
        return new MediatedResourceService(policy, port, new InMemoryResourceArchive());
    }

    private static ResourceAccessRequest read() {
        return new ResourceAccessRequest(ACTOR, DOC, ResourceOperation.READ_CONTENT, "test");
    }

    private static ResourceAccessRequest metadata() {
        return new ResourceAccessRequest(ACTOR, DOC, ResourceOperation.READ_METADATA, "test");
    }

    private static String text(MediatedResult<BronzeContent> result) {
        assertTrue(String.valueOf(result.errorMessage()), result.isSuccess());
        return new String(result.value().content(), StandardCharsets.UTF_8);
    }

    private static Policy allowing() {
        return new Policy();
    }

    /** Allows every operation unless a specific decision is configured, and records what it saw. */
    private static final class Policy implements ResourceAccessPolicy {
        final Map<ResourceOperation, ResourceAccessDecision> decisions =
                new EnumMap<ResourceOperation, ResourceAccessDecision>(ResourceOperation.class);
        final List<ResourceOperation> seen = new ArrayList<ResourceOperation>();

        @Override
        public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
            seen.add(request.operation());
            ResourceAccessDecision decision = decisions.get(request.operation());
            return decision != null ? decision : ResourceAccessDecision.allow();
        }
    }

    private static final class FakePort implements AcquisitionPort {
        String text = "content";
        boolean absent;
        boolean metadata;
        int fetches;

        @Override
        public BronzeContent fetchContent(BookmarkUri uri) throws IOException {
            if (absent) {
                throw new SourceAbsentException("gone");
            }
            fetches++;
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            return new BronzeContent(uri, bytes, ContentHasher.digest(bytes), System.currentTimeMillis());
        }

        @Override
        public BronzeListing listChildren(BookmarkUri uri) {
            return new BronzeListing(uri, Collections.<BronzeListing.Entry>emptyList(), 1L);
        }

        @Override
        public boolean offersMetadata(BookmarkUri uri) {
            return metadata;
        }

        @Override
        public BronzeMetadata fetchMetadata(BookmarkUri uri, AcquisitionCapability capability) throws IOException {
            if (absent) {
                throw new SourceAbsentException("gone");
            }
            return new BronzeMetadata(uri, "doc.txt", "text/plain",
                    text.getBytes(StandardCharsets.UTF_8).length, 1L, 1L);
        }
    }
}
