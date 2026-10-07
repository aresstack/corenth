package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionPort;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ArchivedResource;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeMetadata;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ContentHasher;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalQuery;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessDecisionType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessReasonCode;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AcceptanceDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PolicyReason;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.DigestChangeDetection;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition.DerivativeDispositionPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope.ResourceSizePolicy;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Focused tests for #10 Slice 1: the lifecycle acquires exclusively through the
 * {@link MediatedResourceAccess} contract, and Tamias decisions keep their semantics.
 *
 * <p>These tests run against fakes and an in-memory counter. They prove the contract and
 * the decision mapping; they do not prove any external production integration.
 */
public class MediatedLifecycleCoordinatorTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final ActorIdentity INDEXER = new ActorIdentity("indexer", ActorType.SERVICE);
    private static final BookmarkUri DOC_URI = BookmarkUri.parse("file:///virtual/docs/notes.txt");
    private static final VirtualResourceRef DOC = new VirtualResourceRef(DOC_URI, VirtualResourceKind.FILE);

    // ── Contract: the lifecycle reads through MediatedResourceAccess ──

    @Test
    public void lifecycleReadsContent_throughTheMediatedContract_asTheConfiguredActor() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "mediated text"), ResourceAccessDecision.allow());
        RecordingLexicalIndex index = new RecordingLexicalIndex();

        ProcessingResult result = coordinator(access, acceptAll(), index).process(DOC);

        assertEquals(ProcessingResult.Status.INDEXED, result.status());
        assertEquals(1, access.requests.size());
        ResourceAccessRequest request = access.requests.get(0);
        assertEquals(INDEXER, request.actor());
        assertEquals(DOC_URI, request.target());
        assertEquals(ResourceOperation.READ_CONTENT, request.operation());
        assertEquals(ResourceLifecycleCoordinator.LIFECYCLE_PURPOSE, request.purpose());
        assertTrue(index.indexed.contains(DOC));
        assertEquals("notes.txt", index.lastTitle);
    }

    @Test
    public void indexingPolicyDenial_happensBeforeAnyMediatedRead() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "never read"), ResourceAccessDecision.allow());
        ResourcePolicy markdownOnly = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "md-only", Arrays.asList("file"), Arrays.asList("**/*.md"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));

        ProcessingResult result = coordinator(access, markdownOnly, new RecordingLexicalIndex()).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue("a resource the indexing policy rejects must not be acquired", access.requests.isEmpty());
    }

    @Test
    public void sizeLimit_withoutSourceMetadata_boundsTheMediatedRead_andDeniesTheOversizedSource() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextContent = MediatedResult.denied(
                ResourceAccessDecision.deny(AccessReasonCode.TOO_LARGE, "at least 11 bytes > limit 10 bytes"));
        RecordingLexicalIndex index = new RecordingLexicalIndex();

        ProcessingResult result = sized(access, ResourceSizePolicy.maxBytes(10), index).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertEquals("the counter receives the Tamias size policy as acquisition bound",
                ResourceSizePolicy.maxBytes(10), access.acquisitionLimits.get(0));
        ResourceProcessingStep acquisition = step(result, ResourceProcessingStepType.MEDIATED_ACQUISITION);
        assertTrue(acquisition.detail(), acquisition.detail().contains("TOO_LARGE"));
        assertTrue("no ad-hoc invalidation: the counter cached nothing", access.invalidated.isEmpty());
        assertTrue(index.removed.contains(DOC));
    }

    @Test
    public void sizeLimit_onAnAcquiredOrCachedPayload_isDecidedByTheSizePolicy() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "this text is longer than ten bytes"),
                ResourceAccessDecision.allow());

        ProcessingResult result = sized(access, ResourceSizePolicy.maxBytes(10), new RecordingLexicalIndex())
                .process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message(), result.message().startsWith("SIZE_OVER_LIMIT"));
        assertTrue("no ad-hoc invalidation outside a #5 disposition", access.invalidated.isEmpty());
    }

    @Test
    public void sizePolicy_withSourceMetadata_deniesBeforeAnyMediatedRead() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextMetadata = MediatedResult.success(
                new BronzeMetadata(DOC_URI, "notes.txt", "text/plain", 4096L, 1L, 1L), ResourceAccessDecision.allow());
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "never read"), ResourceAccessDecision.allow());

        ProcessingResult result = sized(access, ResourceSizePolicy.maxBytes(10), new RecordingLexicalIndex())
                .process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message(), result.message().startsWith("SIZE_OVER_LIMIT"));
        assertTrue("a known oversized resource is never read", access.requests.isEmpty());
    }

    @Test
    public void sizeLimit_withSourceMetadata_deniesBeforeAnyMediatedRead() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextMetadata = MediatedResult.success(
                new BronzeMetadata(DOC_URI, "notes.txt", "text/plain", 4096L, 1L, 1L), ResourceAccessDecision.allow());
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "never read"), ResourceAccessDecision.allow());
        ResourcePolicy tiny = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "tiny", Arrays.asList("file"), Arrays.asList("**/*"), Collections.<String>emptyList(), 10)));

        ProcessingResult result = coordinator(access, tiny, new RecordingLexicalIndex()).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("maxBytes"));
        assertTrue("a known oversized resource is never read", access.requests.isEmpty());
        assertEquals(ResourceOperation.READ_METADATA, access.metadataRequests.get(0).operation());
    }

    // ── Decision semantics: DENY, ALLOW_CACHED_ONLY, REQUIRE_AUTH, errors ──

    @Test
    public void mediatedDenial_yieldsDenied_andKeepsGlobalIndexStateOfEarlierRuns() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        RecordingLexicalIndex index = new RecordingLexicalIndex();
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        ResourceLifecycleCoordinator coordinator = coordinator(access, acceptAll(), index, archive);

        access.nextContent = MediatedResult.success(bronze(DOC_URI, "visible once"), ResourceAccessDecision.allow());
        assertEquals(ProcessingResult.Status.INDEXED, coordinator.process(DOC).status());

        access.nextContent = MediatedResult.denied(ResourceAccessDecision.deny(
                AccessReasonCode.NOT_VISIBLE_TO_ACTOR, "indexer may not see this resource"));
        ProcessingResult denied = coordinator.process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, denied.status());
        assertTrue(denied.message(), denied.message().contains("NOT_VISIBLE_TO_ACTOR"));
        assertTrue("actor-scoped access denial must not remove derived index state", index.indexed.contains(DOC));
        assertNotNull("actor-scoped access denial must not remove the lifecycle snapshot", archive.find(DOC));
    }

    @Test
    public void indexingPolicyDenial_removesStaleIndexStateOfEarlierRuns() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "indexed then withdrawn"), ResourceAccessDecision.allow());
        RecordingLexicalIndex index = new RecordingLexicalIndex();
        InMemoryResourceArchive archive = new InMemoryResourceArchive();

        assertEquals(ProcessingResult.Status.INDEXED, coordinator(access, acceptAll(), index, archive).process(DOC).status());

        ResourcePolicy markdownOnly = new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "md-only", Arrays.asList("file"), Arrays.asList("**/*.md"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));
        ProcessingResult denied = coordinator(access, markdownOnly, index, archive).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, denied.status());
        assertFalse("lifecycle policy denial withdraws the resource from the index", index.indexed.contains(DOC));
        assertNull(archive.find(DOC));
    }

    @Test
    public void allowCachedOnly_withoutCachedContent_yieldsDenied_withoutAcquisition() {
        CountingAcquisitionPort acquisition = new CountingAcquisitionPort();
        MediatedResourceService counter = new MediatedResourceService(
                decide(ResourceAccessDecision.cachedOnly("offline mode"), ResourceAccessDecision.allow()),
                acquisition, new InMemoryResourceArchive());

        ProcessingResult result = coordinator(counter, acceptAll(), new RecordingLexicalIndex()).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message(), result.message().contains(AccessReasonCode.CACHE_ONLY_ALLOWED.name()));
        assertEquals(0, acquisition.fetches);
    }

    @Test
    public void allowCachedOnly_withCachedContent_indexesFromTheBronzeCache_withoutAcquisition() {
        CountingAcquisitionPort acquisition = new CountingAcquisitionPort();
        MediatedResourceService counter = new MediatedResourceService(
                decide(ResourceAccessDecision.cachedOnly("offline mode"), ResourceAccessDecision.allow()),
                acquisition, new InMemoryResourceArchive());
        counter.storeBronzeContent(bronze(DOC_URI, "already in the bronze archive"));
        RecordingLexicalIndex index = new RecordingLexicalIndex();

        ProcessingResult result = coordinator(counter, acceptAll(), index).process(DOC);

        assertEquals(ProcessingResult.Status.INDEXED, result.status());
        assertTrue(index.indexed.contains(DOC));
        assertEquals(0, acquisition.fetches);
    }

    @Test
    public void allow_withCacheMiss_acquiresOnce_andServesTheCacheAfterwards() {
        CountingAcquisitionPort acquisition = new CountingAcquisitionPort();
        MediatedResourceService counter = new MediatedResourceService(
                decide(ResourceAccessDecision.allow(), ResourceAccessDecision.allow()),
                acquisition, new InMemoryResourceArchive());
        ResourceLifecycleCoordinator coordinator = coordinator(counter, acceptAll(), new RecordingLexicalIndex());

        assertEquals(ProcessingResult.Status.INDEXED, coordinator.process(DOC).status());
        assertEquals(ProcessingResult.Status.UNCHANGED, coordinator.process(DOC).status());
        assertEquals("second read is served from the counter cache", 1, acquisition.fetches);
    }

    @Test
    public void allowThenCachedOnlyOnExternalFetch_withoutCache_yieldsDenied_withoutAcquisition() {
        // READ_CONTENT is allowed, but FETCH_EXTERNAL answers ALLOW_CACHED_ONLY on a cache miss:
        // the counter withholds the content with an "allowed" decision type. This must still be
        // DENIED, not an acquisition failure.
        CountingAcquisitionPort acquisition = new CountingAcquisitionPort();
        MediatedResourceService counter = new MediatedResourceService(
                decide(ResourceAccessDecision.allow(), ResourceAccessDecision.cachedOnly("offline mode")),
                acquisition, new InMemoryResourceArchive());

        ProcessingResult result = coordinator(counter, acceptAll(), new RecordingLexicalIndex()).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message(), result.message().contains(AccessReasonCode.CACHE_ONLY_ALLOWED.name()));
        assertEquals(0, acquisition.fetches);
    }

    @Test
    public void requireSourceCheckOnExternalFetch_yieldsDenied_withoutAcquisition() {
        CountingAcquisitionPort acquisition = new CountingAcquisitionPort();
        MediatedResourceService counter = new MediatedResourceService(
                decide(ResourceAccessDecision.allow(), new ResourceAccessDecision(
                        AccessDecisionType.REQUIRE_SOURCE_CHECK, AccessReasonCode.SOURCE_DENIED, "source must be checked")),
                acquisition, new InMemoryResourceArchive());

        ProcessingResult result = coordinator(counter, acceptAll(), new RecordingLexicalIndex()).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message(), result.message().contains("REQUIRE_SOURCE_CHECK"));
        assertEquals(0, acquisition.fetches);
    }

    @Test
    public void preAcquisitionPolicyEvaluation_receivesSizeUnknown_notZero() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "some bytes"), ResourceAccessDecision.allow());
        final List<Long> seenSizes = new ArrayList<Long>();
        ResourcePolicy rejectEmpty = new ResourcePolicy() {
            @Override
            public PolicyReason evaluate(VirtualResourceRef ref, long sizeBytes) {
                seenSizes.add(Long.valueOf(sizeBytes));
                if (sizeBytes == 0L) {
                    return new PolicyReason(AcceptanceDecision.DENY, "empty resources are skipped");
                }
                return new PolicyReason(AcceptanceDecision.ACCEPT, "accepted");
            }
        };
        RecordingLexicalIndex index = new RecordingLexicalIndex();

        ProcessingResult result = coordinator(access, rejectEmpty, index).process(DOC);

        assertEquals(ProcessingResult.Status.INDEXED, result.status());
        assertEquals("a skip-empty rule must not reject the pre-acquisition evaluation", 1, access.requests.size());
        assertEquals(Long.valueOf(ResourcePolicy.SIZE_UNKNOWN), seenSizes.get(0));
        assertEquals(Long.valueOf("some bytes".getBytes(UTF_8).length), seenSizes.get(1));
        assertTrue(index.indexed.contains(DOC));
    }

    /**
     * Former {@code knownGap_blacklistedAfterIndexing_staysInTheIndex_untilAWithdrawalPathExists},
     * part 1 (#10 Slice 5): an actor-scoped access denial alone is no lifecycle decision, so the
     * index entry stays. Withdrawal needs an explicit removal in the record (part 2).
     */
    @Test
    public void blacklistedAfterIndexing_withoutRecordedRemoval_staysInTheIndex() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        RecordingLexicalIndex index = new RecordingLexicalIndex();
        ResourceLifecycleCoordinator coordinator = coordinator(access, acceptAll(), index);

        access.nextContent = MediatedResult.success(bronze(DOC_URI, "indexed before blacklisting"), ResourceAccessDecision.allow());
        assertEquals(ProcessingResult.Status.INDEXED, coordinator.process(DOC).status());

        access.nextContent = MediatedResult.denied(ResourceAccessDecision.deny(
                AccessReasonCode.BLACKLISTED, "resource is blacklisted"));
        ProcessingResult denied = coordinator.process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, denied.status());
        assertTrue(denied.message(), denied.message().contains("BLACKLISTED"));
        assertTrue("an access denial alone does not withdraw the index entry", index.indexed.contains(DOC));
    }

    // ── Resource-level admission vs. actor-level access (#5, #10 Slice 5) ──

    @Test
    public void indexingPolicyDenyAfterIndexing_withdrawsThroughTheTamiasDisposition() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        RecordingLexicalIndex index = new RecordingLexicalIndex();
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "indexed before the exclusion"),
                ResourceAccessDecision.allow());
        assertEquals(ProcessingResult.Status.INDEXED, coordinator(access, acceptAll(), index, archive).process(DOC).status());

        ProcessingResult excluded = coordinator(access, excludeAll(), index, archive).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, excluded.status());
        assertFalse(index.indexed.contains(DOC));
        assertTrue(step(excluded, ResourceProcessingStepType.DERIVED_STATE_CLEANUP).detail(),
                step(excluded, ResourceProcessingStepType.DERIVED_STATE_CLEANUP).detail()
                        .contains("NOT_ADMITTED_WHILE_INDEXED"));
        ArchivedResource record = archive.records().findByRef(DOC);
        assertFalse(record.isIndexed());
        assertEquals("#33: the history stays", 1, record.versions().size());
        assertTrue("admission concerns the index, the payload stays cached", access.invalidated.isEmpty());
    }

    @Test
    public void indexingPolicyDeny_withoutIndexedVersion_withdrawsNothing() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        RecordingLexicalIndex index = new RecordingLexicalIndex();
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "indexed, then withdrawn"),
                ResourceAccessDecision.allow());
        coordinator(access, acceptAll(), index, archive).process(DOC);
        coordinator(access, excludeAll(), index, archive).process(DOC);
        index.removed.clear();

        ProcessingResult again = coordinator(access, excludeAll(), index, archive).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, again.status());
        assertTrue("NONE/NOT_ADMITTED_NOT_INDEXED touches no index", index.removed.isEmpty());
        for (ResourceProcessingStep step : again.steps()) {
            assertNotEquals(ResourceProcessingStepType.DERIVED_STATE_CLEANUP, step.type());
        }
    }

    @Test
    public void sizeRejectionAfterIndexing_withdrawsThroughTheSameDispositionPath() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        RecordingLexicalIndex index = new RecordingLexicalIndex();
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "small"), ResourceAccessDecision.allow());
        ResourceLifecycleCoordinator sized = sized(access, ResourceSizePolicy.maxBytes(10), index);
        assertEquals(ProcessingResult.Status.INDEXED, sized.process(DOC).status());

        access.nextContent = MediatedResult.success(bronze(DOC_URI, "grown beyond ten bytes"),
                ResourceAccessDecision.allow());
        ProcessingResult rejected = sized.process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, rejected.status());
        assertEquals("index entry withdrawn (NOT_ADMITTED_WHILE_INDEXED)",
                step(rejected, ResourceProcessingStepType.DERIVED_STATE_CLEANUP).detail());
        assertFalse(index.indexed.contains(DOC));
    }

    /**
     * Former {@code knownGap_blacklistedAfterIndexing_staysInTheIndex_untilAWithdrawalPathExists},
     * part 2 (#10 Slice 5): after the explicit {@code deleteEntry} decision the record carries a
     * removal; the next run executes the Tamias disposition and withdraws the index entry while
     * the history stays.
     */
    @Test
    public void blacklistedAfterExplicitDeleteEntry_isWithdrawnFromTheIndex_andKeepsTheHistory() {
        final boolean[] blacklisted = {false};
        ResourceAccessPolicy policy = new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (blacklisted[0] && request.operation() != ResourceOperation.DELETE_ARCHIVE_ENTRY) {
                    return ResourceAccessDecision.deny(AccessReasonCode.BLACKLISTED, "resource is blacklisted");
                }
                return ResourceAccessDecision.allow();
            }
        };
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        CountingAcquisitionPort acquisition = new CountingAcquisitionPort();
        MediatedResourceService counter = new MediatedResourceService(policy, acquisition, archive);
        RecordingLexicalIndex index = new RecordingLexicalIndex();
        ResourceLifecycleCoordinator coordinator = coordinator(counter, acceptAll(), index, archive);
        assertEquals(ProcessingResult.Status.INDEXED, coordinator.process(DOC).status());

        blacklisted[0] = true;
        assertTrue(counter.deleteEntry(new ResourceAccessRequest(
                INDEXER, DOC_URI, ResourceOperation.DELETE_ARCHIVE_ENTRY, "blacklist")).isSuccess());
        ProcessingResult denied = coordinator.process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, denied.status());
        assertTrue(denied.message(), denied.message().contains("BLACKLISTED"));
        assertFalse("the explicit removal withdraws the index entry", index.indexed.contains(DOC));
        ArchivedResource record = archive.records().findByRef(DOC);
        assertFalse(record.isIndexed());
        assertTrue(record.isRemovedAtSource());
        assertEquals("#33: the history stays", 1, record.versions().size());
        assertEquals("no acquisition for a blacklisted resource", 1, acquisition.fetches);
    }

    @Test
    public void cachedPayloadOlderThanTheRecordedRemoval_isNoProofOfExistence() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        RecordingLexicalIndex index = new RecordingLexicalIndex();
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        ResourceLifecycleCoordinator coordinator = coordinator(access, acceptAll(), index, archive);
        access.nextContent = MediatedResult.success(bronze(DOC_URI, "old payload"), ResourceAccessDecision.allow());
        assertEquals(ProcessingResult.Status.INDEXED, coordinator.process(DOC).status());

        archive.removeByUri(DOC_URI);
        ProcessingResult result = coordinator.process(DOC);

        assertEquals("the payload was fetched before the removal", ProcessingResult.Status.REMOVED, result.status());
        assertFalse(index.indexed.contains(DOC));
        ArchivedResource record = archive.records().findByRef(DOC);
        assertTrue("the removal stands", record.isRemovedAtSource());
        assertFalse(record.isIndexed());
        assertTrue(access.invalidated.contains(DOC_URI));
    }

    @Test
    public void sourceAbsentWithoutRecord_failsAsNotFound_withoutTouchingTheIndex() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextContent = MediatedResult.failure(MediatedResult.Failure.SOURCE_ABSENT, "gone");
        InMemoryResourceArchive archive = new InMemoryResourceArchive();

        ProcessingResult result = coordinator(access, acceptAll(), new RecordingLexicalIndex(), archive).process(DOC);

        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertEquals(ResourceProcessingFailure.Reason.SOURCE_NOT_FOUND, result.failure().reason());
        assertNull("no record for a resource that was never seen", archive.records().findByRef(DOC));
    }

    @Test
    public void refreshPermitted_reacquiresTheChangedSource_andRecordsTheNewVersion() {
        final String[] source = {"first version"};
        AcquisitionPort changing = new AcquisitionPort() {
            @Override
            public BronzeContent fetchContent(BookmarkUri uri) {
                return bronze(uri, source[0]);
            }

            @Override
            public BronzeListing listChildren(BookmarkUri uri) {
                return new BronzeListing(uri, Collections.<BronzeListing.Entry>emptyList(), 1L);
            }
        };
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        MediatedResourceService counter = new MediatedResourceService(decide(ResourceAccessDecision.allow(),
                ResourceAccessDecision.allow(), ResourceAccessDecision.allow()), changing, archive);
        ResourceLifecycleCoordinator coordinator = coordinator(counter, acceptAll(), new RecordingLexicalIndex(), archive);
        assertEquals(ProcessingResult.Status.INDEXED, coordinator.process(DOC).status());

        source[0] = "second version";
        assertEquals(ProcessingResult.Status.INDEXED, coordinator.process(DOC).status());
        assertEquals(ProcessingResult.Status.UNCHANGED, coordinator.process(DOC).status());

        ArchivedResource record = archive.records().findByRef(DOC);
        assertEquals(2, record.versions().size());
        assertEquals(2L, record.indexedVersion().version().sequence());
    }

    @Test
    public void requireAuthOnExternalFetch_yieldsDenied_untilTheAdytonStationExists() {
        CountingAcquisitionPort acquisition = new CountingAcquisitionPort();
        MediatedResourceService counter = new MediatedResourceService(
                decide(ResourceAccessDecision.allow(), new ResourceAccessDecision(
                        com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessDecisionType.REQUIRE_AUTH,
                        AccessReasonCode.SOURCE_AUTH_REQUIRED, "source needs credentials")),
                acquisition, new InMemoryResourceArchive());

        ProcessingResult result = coordinator(counter, acceptAll(), new RecordingLexicalIndex()).process(DOC);

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message(), result.message().contains("REQUIRE_AUTH"));
        assertEquals("no acquisition without prepared access", 0, acquisition.fetches);
    }

    @Test
    public void acquisitionError_yieldsFailed() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.nextContent = MediatedResult.error("Acquisition failed: connector exploded");

        ProcessingResult result = coordinator(access, acceptAll(), new RecordingLexicalIndex()).process(DOC);

        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertTrue(result.message(), result.message().contains("connector exploded"));
    }

    @Test
    public void mediatedAccessThrowing_yieldsFailed_notAnException() {
        RecordingMediatedAccess access = new RecordingMediatedAccess();
        access.failure = new IllegalStateException("counter unavailable");

        ProcessingResult result = coordinator(access, acceptAll(), new RecordingLexicalIndex()).process(DOC);

        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertTrue(result.message(), result.message().contains("counter unavailable"));
    }

    // ── Construction ──

    @Test
    public void constructorRejectsMissingMediatedAccessOrActor() {
        try {
            new ResourceLifecycleCoordinator(null, INDEXER, plainText(), acceptAll(),
                    new InMemoryResourceArchive(), new RecordingLexicalIndex());
            fail("mediatedAccess must be required");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("mediatedAccess"));
        }
        try {
            new ResourceLifecycleCoordinator(new RecordingMediatedAccess(), null, plainText(), acceptAll(),
                    new InMemoryResourceArchive(), new RecordingLexicalIndex());
            fail("actor must be required");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("actor"));
        }
    }

    @Test
    public void filenameHint_isDerivedFromTheLastPathSegment() {
        assertEquals("notes.txt", ResourceLifecycleCoordinator.filenameHint(DOC_URI));
        assertEquals("with space.md", ResourceLifecycleCoordinator.filenameHint(
                BookmarkUri.parse("file:///tmp/with%20space.md")));
        assertEquals("MEMBER", ResourceLifecycleCoordinator.filenameHint(
                BookmarkUri.parse("ndv://host/LIB/MEMBER")));
        assertEquals("tmp", ResourceLifecycleCoordinator.filenameHint(BookmarkUri.parse("file:///tmp/")));
        assertNull(ResourceLifecycleCoordinator.filenameHint(BookmarkUri.parse("file:///")));
        assertNull(ResourceLifecycleCoordinator.filenameHint(null));
    }

    // ── Helpers ──

    private static ResourceLifecycleCoordinator coordinator(MediatedResourceAccess access,
                                                            ResourcePolicy policy,
                                                            LexicalIndex index) {
        return coordinator(access, policy, index, new InMemoryResourceArchive());
    }

    private static ResourceLifecycleCoordinator coordinator(MediatedResourceAccess access,
                                                            ResourcePolicy policy,
                                                            LexicalIndex index,
                                                            InMemoryResourceArchive archive) {
        return new ResourceLifecycleCoordinator(access, INDEXER, plainText(), policy, archive, index);
    }

    private static ResourceLifecycleCoordinator sized(MediatedResourceAccess access, ResourceSizePolicy sizePolicy,
                                                      LexicalIndex index) {
        return new ResourceLifecycleCoordinator(access, INDEXER, plainText(), acceptAll(),
                new InMemoryResourceArchive().records(), index, null, new DigestChangeDetection(),
                new DerivativeDispositionPolicy(), sizePolicy, Clock.systemUTC());
    }

    private static ResourceProcessingStep step(ProcessingResult result, ResourceProcessingStepType type) {
        for (ResourceProcessingStep step : result.steps()) {
            if (step.type() == type) {
                return step;
            }
        }
        throw new AssertionError("no step " + type + " in " + result.steps());
    }

    private static ResourcePolicy excludeAll() {
        return new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "exclude-all", Collections.<String>emptyList(), Arrays.asList("**/*"),
                Arrays.asList("**/*"), Long.MAX_VALUE)));
    }

    private static ResourcePolicy acceptAll() {
        return new PatternResourcePolicy(Arrays.asList(new IndexingRule(
                "accept-all", Collections.<String>emptyList(), Arrays.asList("**/*"),
                Collections.<String>emptyList(), Long.MAX_VALUE)));
    }

    private static ContentInspector plainText() {
        return new ContentInspector() {
            @Override
            public InspectionResult inspect(VirtualResourceRef ref, byte[] content, String filenameHint) {
                return InspectionResult.success("text/plain", Arrays.asList(new String(content, UTF_8)));
            }
        };
    }

    private static BronzeContent bronze(BookmarkUri uri, String text) {
        byte[] bytes = text.getBytes(UTF_8);
        return new BronzeContent(uri, bytes, ContentHasher.digest(bytes), 1L);
    }

    /**
     * Policy returning one decision for the primary operation and another for FETCH_EXTERNAL.
     * REFRESH_EXTERNAL is answered with ALLOW_CACHED_ONLY: these policies configure no refresh.
     */
    private static ResourceAccessPolicy decide(final ResourceAccessDecision primary,
                                               final ResourceAccessDecision external) {
        return decide(primary, external, ResourceAccessDecision.cachedOnly("refresh not configured"));
    }

    /** Policy with separate decisions for the primary operation, FETCH_EXTERNAL and REFRESH_EXTERNAL. */
    private static ResourceAccessPolicy decide(final ResourceAccessDecision primary,
                                               final ResourceAccessDecision external,
                                               final ResourceAccessDecision refresh) {
        return new ResourceAccessPolicy() {
            @Override
            public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
                if (request.operation() == ResourceOperation.FETCH_EXTERNAL) {
                    return external;
                }
                return request.operation() == ResourceOperation.REFRESH_EXTERNAL ? refresh : primary;
            }
        };
    }

    private static final class RecordingMediatedAccess implements MediatedResourceAccess {
        final List<ResourceAccessRequest> requests = new ArrayList<ResourceAccessRequest>();
        final List<ResourceAccessRequest> metadataRequests = new ArrayList<ResourceAccessRequest>();
        final List<BookmarkUri> invalidated = new ArrayList<BookmarkUri>();
        final List<ResourceSizePolicy> acquisitionLimits = new ArrayList<ResourceSizePolicy>();
        MediatedResult<BronzeContent> nextContent;
        MediatedResult<BronzeMetadata> nextMetadata;
        RuntimeException failure;

        @Override
        public MediatedResult<BronzeContent> readContent(ResourceAccessRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return nextContent;
        }

        @Override
        public MediatedResult<BronzeListing> listChildren(ResourceAccessRequest request) {
            requests.add(request);
            return MediatedResult.error("listing is not used by the lifecycle");
        }

        @Override
        public MediatedResult<BronzeContent> refreshContent(ResourceAccessRequest request,
                                                            ResourceSizePolicy acquisitionLimit) {
            acquisitionLimits.add(acquisitionLimit);
            return readContent(request);
        }

        @Override
        public MediatedResult<BronzeMetadata> readMetadata(ResourceAccessRequest request) {
            metadataRequests.add(request);
            return nextMetadata != null ? nextMetadata
                    : MediatedResult.<BronzeMetadata>failure(MediatedResult.Failure.METADATA_UNAVAILABLE, "no metadata");
        }

        @Override
        public void invalidatePayload(BookmarkUri uri) {
            invalidated.add(uri);
        }
    }

    private static final class CountingAcquisitionPort implements AcquisitionPort {
        int fetches;

        @Override
        public BronzeContent fetchContent(BookmarkUri uri) {
            fetches++;
            return bronze(uri, "acquired content");
        }

        @Override
        public BronzeListing listChildren(BookmarkUri uri) {
            return new BronzeListing(uri, Collections.<BronzeListing.Entry>emptyList(), 1L);
        }
    }

    private static final class RecordingLexicalIndex implements LexicalIndex {
        final Set<VirtualResourceRef> indexed = new HashSet<VirtualResourceRef>();
        final Set<VirtualResourceRef> removed = new HashSet<VirtualResourceRef>();
        String lastTitle;

        @Override
        public void index(LexicalDocument document) {
            indexed.add(document.resourceRef());
            lastTitle = document.title();
        }

        @Override
        public List<LexicalSearchResult> search(LexicalQuery query) {
            return Collections.emptyList();
        }

        @Override
        public void remove(VirtualResourceRef resourceRef) {
            indexed.remove(resourceRef);
            removed.add(resourceRef);
        }

        @Override
        public void commit() {
        }

        @Override
        public void close() throws IOException {
        }
    }
}
