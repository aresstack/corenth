package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessDecisionType;
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
import java.util.List;

import static org.junit.Assert.*;

/** Counter-side contract of the #10 Slice 3 access station. */
public class MediatedAccessPreparationTest {

    private static final ActorIdentity ACTOR = new ActorIdentity("svc", ActorType.SERVICE);
    private static final BookmarkUri URI = BookmarkUri.parse("ftp" + "://host/A.B");

    @Test
    public void allowedAcquisition_asksTheStation_andAcquiresWithoutCapabilityWhenNotRequired() {
        RecordingStation station = new RecordingStation(AcquisitionAccess.notRequired());
        RecordingPort port = new RecordingPort();
        MediatedResourceService counter = counter(AccessDecisionType.ALLOW, port, station);

        MediatedResult<BronzeContent> result = counter.readContent(read());

        assertTrue(result.isSuccess());
        assertEquals(1, station.requests.size());
        AcquisitionAccessRequest prepared = station.requests.get(0);
        assertEquals(URI, prepared.target());
        assertEquals(ResourceOperation.READ_CONTENT, prepared.operation());
        assertEquals(ACTOR, prepared.actor());
        assertEquals(Collections.<AcquisitionCapability>singletonList(null), port.capabilities);
    }

    @Test
    public void requireAuth_withoutAnAuthenticatingStation_isWithheldAsTheTamiasDecision() {
        RecordingPort port = new RecordingPort();
        MediatedResourceService counter = counter(AccessDecisionType.REQUIRE_AUTH, port,
                new RecordingStation(AcquisitionAccess.notRequired()));

        MediatedResult<BronzeContent> result = counter.readContent(read());

        assertFalse(result.isSuccess());
        assertEquals(AccessDecisionType.REQUIRE_AUTH, result.decision().type());
        assertTrue(port.capabilities.isEmpty());
    }

    @Test
    public void legacyConstructor_keepsRequireAuthWithheld() {
        RecordingPort port = new RecordingPort();
        MediatedResourceService counter = new MediatedResourceService(
                policy(AccessDecisionType.REQUIRE_AUTH), port, new InMemoryResourceArchive());

        MediatedResult<BronzeContent> result = counter.readContent(read());

        assertEquals(AccessDecisionType.REQUIRE_AUTH, result.decision().type());
        assertTrue(port.capabilities.isEmpty());
    }

    @Test
    public void grantedCapability_isPassedToThePort_andClosedAfterwards() {
        TestCapability capability = new TestCapability();
        RecordingPort port = new RecordingPort();
        MediatedResourceService counter = counter(AccessDecisionType.REQUIRE_AUTH, port,
                new RecordingStation(AcquisitionAccess.granted(capability)));

        MediatedResult<BronzeContent> result = counter.readContent(read());

        assertTrue(result.isSuccess());
        assertSame(capability, port.capabilities.get(0));
        assertEquals(1, capability.closeCalls);
    }

    @Test
    public void grantedCapability_isClosedWhenAcquisitionFails() {
        TestCapability capability = new TestCapability();
        RecordingPort port = new RecordingPort();
        port.fail = true;
        MediatedResourceService counter = counter(AccessDecisionType.ALLOW, port,
                new RecordingStation(AcquisitionAccess.granted(capability)));

        MediatedResult<BronzeContent> result = counter.readContent(read());

        assertEquals(MediatedResult.Failure.ACQUISITION_FAILED, result.failure());
        assertNull(result.decision());
        assertEquals(1, capability.closeCalls);
    }

    @Test
    public void cancelledUnavailableAndFailedPreparation_areDistinctTypedFailures() {
        assertEquals(MediatedResult.Failure.AUTHENTICATION_CANCELLED,
                failureFor(AcquisitionAccess.cancelled("cancelled")));
        assertEquals(MediatedResult.Failure.AUTHENTICATION_UNAVAILABLE,
                failureFor(AcquisitionAccess.unavailable("none")));
        assertEquals(MediatedResult.Failure.AUTHENTICATION_FAILED,
                failureFor(AcquisitionAccess.failed("rejected")));
    }

    @Test
    public void deniedOrCachedOnlyFetch_neverReachesTheStation() {
        RecordingStation station = new RecordingStation(AcquisitionAccess.notRequired());
        counter(AccessDecisionType.DENY, new RecordingPort(), station).readContent(read());
        counter(AccessDecisionType.ALLOW_CACHED_ONLY, new RecordingPort(), station).readContent(read());
        counter(AccessDecisionType.REQUIRE_SOURCE_CHECK, new RecordingPort(), station).readContent(read());

        assertTrue(station.requests.isEmpty());
    }

    @Test
    public void cacheHit_doesNotPrepareAccessAgain() {
        RecordingStation station = new RecordingStation(AcquisitionAccess.notRequired());
        MediatedResourceService counter = counter(AccessDecisionType.ALLOW, new RecordingPort(), station);

        counter.readContent(read());
        counter.readContent(read());

        assertEquals(1, station.requests.size());
    }

    @Test
    public void listing_isPreparedForTheListOperation() {
        RecordingStation station = new RecordingStation(AcquisitionAccess.notRequired());
        MediatedResourceService counter = counter(AccessDecisionType.ALLOW, new RecordingPort(), station);

        MediatedResult<BronzeListing> result = counter.listChildren(
                new ResourceAccessRequest(ACTOR, URI, ResourceOperation.LIST_CHILDREN, "test"));

        assertTrue(result.isSuccess());
        assertEquals(ResourceOperation.LIST_CHILDREN, station.requests.get(0).operation());
    }

    @Test
    public void stationRuntimeFailure_isAnAcquisitionFailure() {
        AcquisitionAccessPort broken = new AcquisitionAccessPort() {
            @Override
            public AcquisitionAccess prepare(AcquisitionAccessRequest request) {
                throw new IllegalStateException("broken station");
            }
        };
        MediatedResult<BronzeContent> result = counter(AccessDecisionType.ALLOW, new RecordingPort(), broken)
                .readContent(read());

        assertEquals(MediatedResult.Failure.ACQUISITION_FAILED, result.failure());
    }

    @Test
    public void defaultPortMethods_refuseCapabilitiesTheyCannotUse() throws IOException {
        AcquisitionPort plain = new AcquisitionPort() {
            @Override
            public BronzeContent fetchContent(BookmarkUri uri) {
                return content(uri);
            }

            @Override
            public BronzeListing listChildren(BookmarkUri uri) {
                return new BronzeListing(uri, Collections.<BronzeListing.Entry>emptyList(), 0L);
            }
        };
        assertNotNull(plain.fetchContent(URI, null));
        try {
            plain.fetchContent(URI, new TestCapability());
            fail("expected refusal");
        } catch (IOException expected) {
            // a port without authenticated sources must not silently ignore a capability
        }
    }

    private static MediatedResult.Failure failureFor(AcquisitionAccess access) {
        RecordingPort port = new RecordingPort();
        MediatedResult<BronzeContent> result = counter(AccessDecisionType.REQUIRE_AUTH, port,
                new RecordingStation(access)).readContent(read());
        assertFalse(result.isSuccess());
        assertNull(result.decision());
        assertTrue(port.capabilities.isEmpty());
        return result.failure();
    }

    private static MediatedResourceService counter(AccessDecisionType fetchDecision, AcquisitionPort port,
                                                   AcquisitionAccessPort station) {
        return new MediatedResourceService(policy(fetchDecision), port, new InMemoryResourceArchive(), station);
    }

    private static ResourceAccessPolicy policy(final AccessDecisionType fetchDecision) {
        return new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.operation() == ResourceOperation.FETCH_EXTERNAL) {
                    return new ResourceAccessDecision(fetchDecision,
                            fetchDecision == AccessDecisionType.ALLOW ? AccessReasonCode.ALLOWED
                                    : AccessReasonCode.SOURCE_AUTH_REQUIRED, "test");
                }
                return ResourceAccessDecision.allow();
            }
        };
    }

    private static ResourceAccessRequest read() {
        return new ResourceAccessRequest(ACTOR, URI, ResourceOperation.READ_CONTENT, "test");
    }

    private static BronzeContent content(BookmarkUri uri) {
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        return new BronzeContent(uri, bytes, ContentHasher.digest(bytes), 0L);
    }

    private static final class RecordingStation implements AcquisitionAccessPort {
        private final AcquisitionAccess answer;
        final List<AcquisitionAccessRequest> requests = new ArrayList<AcquisitionAccessRequest>();

        RecordingStation(AcquisitionAccess answer) {
            this.answer = answer;
        }

        @Override
        public AcquisitionAccess prepare(AcquisitionAccessRequest request) {
            requests.add(request);
            return answer;
        }
    }

    private static final class RecordingPort implements AcquisitionPort {
        final List<AcquisitionCapability> capabilities = new ArrayList<AcquisitionCapability>();
        boolean fail;

        @Override
        public BronzeContent fetchContent(BookmarkUri uri) throws IOException {
            return fetchContent(uri, null);
        }

        @Override
        public BronzeListing listChildren(BookmarkUri uri) throws IOException {
            return listChildren(uri, null);
        }

        @Override
        public BronzeContent fetchContent(BookmarkUri uri, AcquisitionCapability capability) throws IOException {
            capabilities.add(capability);
            if (fail) {
                throw new IOException("source failed");
            }
            return content(uri);
        }

        @Override
        public BronzeListing listChildren(BookmarkUri uri, AcquisitionCapability capability) {
            capabilities.add(capability);
            return new BronzeListing(uri, Collections.<BronzeListing.Entry>emptyList(), 0L);
        }
    }

    private static final class TestCapability implements AcquisitionCapability {
        int closeCalls;

        @Override public String grantId() { return "grant"; }
        @Override public String targetSystem() { return "ftp" + "://host"; }
        @Override public long expiresAtEpochMillis() { return Long.MAX_VALUE; }
        @Override public void close() { closeCalls++; }
    }
}
