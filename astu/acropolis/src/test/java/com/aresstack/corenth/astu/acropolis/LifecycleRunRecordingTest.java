package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeMetadata;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ContentHasher;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalQuery;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aresstack.corenth.astu.acropolis.ResourceProcessingStepType.*;
import static org.junit.Assert.*;

/**
 * The lifecycle records its steps, outcomes and typed failures (#10 Slices 3 and 4).
 * Runs against fakes; it proves the recording, not an external integration.
 */
public class LifecycleRunRecordingTest {

    private static final ActorIdentity INDEXER = new ActorIdentity("indexer", ActorType.SERVICE);

    @Test
    public void indexedResource_recordsTheFullPlanInOrder() {
        Fixture fixture = new Fixture();
        fixture.access.put("a.txt", "alpha");

        ProcessingResult result = fixture.lifecycle.process(ref("a.txt"));

        assertEquals(ResourceProcessingOutcome.INDEXED, result.outcome());
        assertEquals(Arrays.asList(INDEXING_POLICY_BEFORE_ACQUISITION, SOURCE_METADATA, MEDIATED_ACQUISITION,
                INDEXING_POLICY_WITH_SIZE, CHANGE_DETECTION, CONTENT_INSPECTION, LEXICAL_INDEXING, RECORD_UPDATE),
                types(result));
        assertTrue(ResourceProcessingPlan.standard().accepts(result.steps()));
        assertNull(result.failure());
    }

    @Test
    public void unchangedResource_stopsAtChangeDetection_withoutExtractionOrIndexWrite() {
        Fixture fixture = new Fixture();
        fixture.access.put("a.txt", "alpha");
        fixture.lifecycle.process(ref("a.txt"));
        int inspections = fixture.inspector.calls;
        int writes = fixture.index.writes;

        ProcessingResult second = fixture.lifecycle.process(ref("a.txt"));

        assertEquals(ResourceProcessingOutcome.UNCHANGED, second.outcome());
        assertEquals(CHANGE_DETECTION, last(second).type());
        assertEquals(ResourceProcessingStep.Status.STOPPED, last(second).status());
        assertEquals("no extraction for unchanged content", inspections, fixture.inspector.calls);
        assertEquals("no index mutation for unchanged content", writes, fixture.index.writes);
    }

    @Test
    public void indexingPolicyDenial_stopsBeforeAcquisition_andCleansDerivedState() {
        Fixture fixture = new Fixture();
        ProcessingResult result = fixture.lifecycle.process(ref("a.bin"));

        assertEquals(ResourceProcessingOutcome.DENIED, result.outcome());
        assertEquals(Arrays.asList(INDEXING_POLICY_BEFORE_ACQUISITION, DERIVED_STATE_CLEANUP), types(result));
        assertEquals(ResourceProcessingStep.Status.STOPPED, result.steps().get(0).status());
    }

    @Test
    public void accessDenial_stopsAtAcquisition_withoutCleanup() {
        Fixture fixture = new Fixture();
        fixture.access.denyAll = true;

        ProcessingResult result = fixture.lifecycle.process(ref("a.txt"));

        assertEquals(ResourceProcessingOutcome.DENIED, result.outcome());
        assertEquals(Arrays.asList(INDEXING_POLICY_BEFORE_ACQUISITION, SOURCE_METADATA, MEDIATED_ACQUISITION),
                types(result));
    }

    @Test
    public void emptyExtraction_isNoExtractableContent_withTypedReason() {
        Fixture fixture = new Fixture();
        fixture.access.put("a.txt", "alpha");
        fixture.inspector.empty = true;

        ProcessingResult result = fixture.lifecycle.process(ref("a.txt"));

        assertEquals(ResourceProcessingOutcome.NO_EXTRACTABLE_CONTENT, result.outcome());
        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertEquals(ResourceProcessingFailure.Reason.NO_INDEXABLE_TEXT, result.failure().reason());
        assertEquals(DERIVED_STATE_CLEANUP, last(result).type());
    }

    @Test
    public void authenticationOutcomes_stayDistinct() {
        assertOutcome(MediatedResult.Failure.AUTHENTICATION_CANCELLED,
                ResourceProcessingOutcome.CANCELLED, ResourceProcessingFailure.Reason.AUTHENTICATION_CANCELLED);
        assertOutcome(MediatedResult.Failure.AUTHENTICATION_FAILED,
                ResourceProcessingOutcome.FAILED, ResourceProcessingFailure.Reason.AUTHENTICATION_FAILED);
        assertOutcome(MediatedResult.Failure.AUTHENTICATION_UNAVAILABLE,
                ResourceProcessingOutcome.FAILED, ResourceProcessingFailure.Reason.AUTHENTICATION_UNAVAILABLE);
        assertOutcome(MediatedResult.Failure.ACQUISITION_FAILED,
                ResourceProcessingOutcome.FAILED, ResourceProcessingFailure.Reason.ACQUISITION_FAILED);
    }

    @Test
    public void failedIndexWrite_isRecordedOnTheIndexingStep() {
        Fixture fixture = new Fixture();
        fixture.access.put("a.txt", "alpha");
        fixture.index.fail = true;

        ProcessingResult result = fixture.lifecycle.process(ref("a.txt"));

        assertEquals(ResourceProcessingFailure.Reason.INDEXING_FAILED, result.failure().reason());
        assertEquals(LEXICAL_INDEXING, last(result).type());
        assertEquals(ResourceProcessingStep.Status.FAILED, last(result).status());
    }

    @Test
    public void runner_processesInOrder_andSummarises() {
        Fixture fixture = new Fixture();
        fixture.access.put("a.txt", "alpha");
        fixture.access.put("b.txt", "beta");
        fixture.lifecycle.process(ref("b.txt"));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);

        ResourceProcessingRun run = new ResourceProcessingRunner(fixture.lifecycle, clock).run(
                new ResourceProcessingRunId("run-1"), Arrays.asList(ref("a.txt"), ref("b.txt"), ref("c.bin")));

        assertEquals(3, run.results().size());
        assertEquals(ref("a.txt"), run.results().get(0).ref());
        assertEquals(1, run.summary().count(ResourceProcessingOutcome.INDEXED));
        assertEquals(1, run.summary().count(ResourceProcessingOutcome.UNCHANGED));
        assertEquals(1, run.summary().count(ResourceProcessingOutcome.DENIED));
        assertEquals(1000L, run.startedAtMillis());
        assertEquals(1000L, run.finishedAtMillis());
    }

    @Test
    public void failedRun_doesNotTurnTheRecordIntoAFailedState() {
        Fixture fixture = new Fixture();
        fixture.access.put("a.txt", "alpha");
        fixture.lifecycle.process(ref("a.txt"));
        fixture.access.failure = MediatedResult.Failure.ACQUISITION_FAILED;

        ProcessingResult failed = fixture.lifecycle.process(ref("a.txt"));

        assertEquals(ResourceProcessingOutcome.FAILED, failed.outcome());
        assertNotNull("the indexed fact survives a failed run", fixture.archive.find(ref("a.txt")));
        assertEquals(1, fixture.archive.records().findByRef(ref("a.txt")).versions().size());
    }

    private static void assertOutcome(MediatedResult.Failure failure, ResourceProcessingOutcome outcome,
                                      ResourceProcessingFailure.Reason reason) {
        Fixture fixture = new Fixture();
        fixture.access.failure = failure;

        ProcessingResult result = fixture.lifecycle.process(ref("a.txt"));

        assertEquals(failure.name(), outcome, result.outcome());
        assertEquals(failure.name(), reason, result.failure().reason());
        assertEquals(MEDIATED_ACQUISITION, last(result).type());
        assertTrue(fixture.index.writes == 0);
    }

    private static VirtualResourceRef ref(String name) {
        return new VirtualResourceRef(BookmarkUri.parse("file:///virtual/" + name), VirtualResourceKind.FILE);
    }

    private static List<ResourceProcessingStepType> types(ProcessingResult result) {
        List<ResourceProcessingStepType> types = new ArrayList<ResourceProcessingStepType>();
        for (ResourceProcessingStep step : result.steps()) {
            types.add(step.type());
        }
        return types;
    }

    private static ResourceProcessingStep last(ProcessingResult result) {
        return result.steps().get(result.steps().size() - 1);
    }

    private static final class Fixture {
        final FakeAccess access = new FakeAccess();
        final CountingInspector inspector = new CountingInspector();
        final CountingIndex index = new CountingIndex();
        final InMemoryResourceArchive archive = new InMemoryResourceArchive();
        final ResourceLifecycleCoordinator lifecycle;

        Fixture() {
            ResourcePolicy textOnly = new PatternResourcePolicy(Collections.singletonList(new IndexingRule(
                    "txt", Collections.singletonList("file"), Collections.singletonList("**/*.txt"),
                    Collections.<String>emptyList(), Long.MAX_VALUE)));
            lifecycle = new ResourceLifecycleCoordinator(access, INDEXER, inspector, textOnly, archive, index);
        }
    }

    private static final class FakeAccess implements MediatedResourceAccess {
        final Map<BookmarkUri, String> contents = new HashMap<BookmarkUri, String>();
        boolean denyAll;
        MediatedResult.Failure failure;

        void put(String name, String text) {
            contents.put(ref(name).uri(), text);
        }

        @Override
        public MediatedResult<BronzeContent> readContent(ResourceAccessRequest request) {
            if (denyAll) {
                return MediatedResult.denied(ResourceAccessDecision.deny(
                        com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessReasonCode.NOT_WHITELISTED, "x"));
            }
            if (failure != null) {
                return MediatedResult.failure(failure, "test " + failure);
            }
            byte[] bytes = contents.get(request.target()).getBytes(StandardCharsets.UTF_8);
            return MediatedResult.success(new BronzeContent(request.target(), bytes, ContentHasher.digest(bytes), 1L),
                    ResourceAccessDecision.allow());
        }

        @Override
        public MediatedResult<BronzeListing> listChildren(ResourceAccessRequest request) {
            return MediatedResult.error("unused");
        }

        @Override
        public MediatedResult<BronzeContent> refreshContent(ResourceAccessRequest request) {
            return readContent(new ResourceAccessRequest(request.actor(), request.target(),
                    ResourceOperation.READ_CONTENT, request.purpose()));
        }

        @Override
        public MediatedResult<BronzeMetadata> readMetadata(ResourceAccessRequest request) {
            return MediatedResult.failure(MediatedResult.Failure.METADATA_UNAVAILABLE, "no metadata in this fake");
        }

        @Override
        public void invalidatePayload(BookmarkUri uri) {
        }
    }

    private static final class CountingInspector implements ContentInspector {
        int calls;
        boolean empty;

        @Override
        public InspectionResult inspect(VirtualResourceRef ref, byte[] content, String filenameHint) {
            calls++;
            return InspectionResult.success("text/plain", empty ? Collections.<String>emptyList()
                    : Collections.singletonList(new String(content, StandardCharsets.UTF_8)));
        }
    }

    private static final class CountingIndex implements LexicalIndex {
        int writes;
        boolean fail;

        @Override
        public void index(LexicalDocument document) throws IOException {
            if (fail) {
                throw new IOException("disk full");
            }
            writes++;
        }

        @Override
        public List<LexicalSearchResult> search(LexicalQuery query) {
            return Collections.emptyList();
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
    }
}
