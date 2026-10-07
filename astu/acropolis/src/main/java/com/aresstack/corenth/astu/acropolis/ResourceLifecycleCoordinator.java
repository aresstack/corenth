package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ArchivedResource;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeMetadata;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceArchiveRepository;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceDigest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LexicalChunker;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AcceptanceDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PolicyReason;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ChangeDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ChangeDetectionStrategy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ChangeReasonCode;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ContentComparison;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.DigestChangeDetection;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.ResourceRecordFacts;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.SourceObservation;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition.CacheAction;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition.DerivativeDisposition;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition.DerivativeDispositionPolicy;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the resource lifecycle pipeline for a single resource.
 *
 * <p>Resources are obtained exclusively through the mediated bronze archive counter
 * ({@link MediatedResourceAccess}, implemented by Chalcotheca's {@code MediatedResourceService}).
 * The coordinator never sees connectors, the acquisition port or secrets; Tamias decides for
 * every read whether the lifecycle actor may receive the content and whether external
 * acquisition or a refresh is permitted. There is no direct provider or connector path
 * (#10 Slice 1).
 *
 * <p>Pipeline ({@link ResourceProcessingPlan#standard()}):
 * <ol>
 *   <li>{@code tamias} {@link ResourcePolicy}: indexing rules before acquisition (size unknown);</li>
 *   <li>source metadata through the counter; a known size is checked against the indexing rules
 *       before any payload is acquired (#10 Slice 5);</li>
 *   <li>{@link MediatedResourceAccess#refreshContent(ResourceAccessRequest)}: mediated read that
 *       re-acquires the payload when Tamias permits {@code REFRESH_EXTERNAL};</li>
 *   <li>{@code tamias} {@link ResourcePolicy}: indexing rules with the actual size; a rejected
 *       payload is invalidated in the counter so that it does not stay cached;</li>
 *   <li>change detection and derivative disposition by Tamias (#5) on a projection of the
 *       resource record (#33); the record observes the version, unchanged content whose
 *       indexed version is current is neither extracted nor indexed again;</li>
 *   <li>{@link ContentInspector}: detect and extract;</li>
 *   <li>{@code anagraphai}: lexical indexing;</li>
 *   <li>record update: the indexed version is recorded in the resource record.</li>
 * </ol>
 *
 * <p>The resource records ({@link ResourceArchiveRepository}) are the only truth about versions,
 * the indexed version and removals at the source. The coordinator maps a record to
 * {@link ResourceRecordFacts} and compares digests with {@link ResourceDigest#equals(Object)};
 * Tamias decides, the coordinator executes. A run never writes a failure or denial state into
 * a record.
 *
 * <p>Every call records the executed steps, the {@link ResourceProcessingOutcome} and a typed
 * {@link ResourceProcessingFailure} (#10 Slice 4); {@link ResourceProcessingRunner} records runs
 * over several resources. Authentication cancellation is {@code CANCELLED}, missing credentials
 * and authentication failure are {@code FAILED} with their own reason codes (#10 Slice 3).
 *
 * <p>Outcome semantics:
 * <ul>
 *   <li>Tamias withholds the content ({@code DENY}, {@code ALLOW_CACHED_ONLY} without cached
 *       content, {@code REQUIRE_AUTH} without a station, {@code REQUIRE_SOURCE_CHECK}):
 *       {@code DENIED}. An actor-scoped access denial alone never removes derived state, not
 *       even for {@code BLACKLISTED}. Only when the record carries an explicit removal
 *       (tombstone, e.g. from {@code deleteEntry}) does the lifecycle execute the Tamias
 *       disposition for the removal and withdraw the index entry; the history stays.</li>
 *   <li>The source confirms the resource is absent: the record observes the removal, the
 *       counter's payload is invalidated and an indexed version is withdrawn: {@code REMOVED}.
 *       A cached payload fetched before a recorded removal is no proof that the resource exists
 *       again and is treated as absent.</li>
 *   <li>An indexing-policy {@code DENY} or a payload without indexable text is a lifecycle
 *       decision for this resource: stale index entries are removed and the indexed-version
 *       fact is withdrawn (the record and its history are kept, #33).</li>
 *   <li>Acquisition errors: {@code FAILED} with a typed reason.</li>
 * </ul>
 *
 * <p>Open gap: a source without metadata still has its payload acquired completely before the
 * size limit applies. The rejected payload is invalidated afterwards, but a bounded acquisition
 * for unknown sizes does not exist yet.
 */
public final class ResourceLifecycleCoordinator {

    /** Purpose recorded on every mediated request issued by this lifecycle. */
    public static final String LIFECYCLE_PURPOSE = "acropolis-lifecycle-indexing";

    private final MediatedResourceAccess mediatedAccess;
    private final ActorIdentity actor;
    private final ContentInspector contentInspector;
    private final ResourcePolicy policy;
    private final ResourceArchiveRepository records;
    private final LexicalIndex lexicalIndex;
    private final LexicalChunker lexicalChunker;
    private final ChangeDetectionStrategy changeDetection;
    private final DerivativeDispositionPolicy dispositionPolicy;
    private final Clock clock;

    /**
     * Creates a coordinator over the records of an in-memory archive, without chunking.
     *
     * @see #ResourceLifecycleCoordinator(MediatedResourceAccess, ActorIdentity, ContentInspector,
     *      ResourcePolicy, ResourceArchiveRepository, LexicalIndex, LexicalChunker,
     *      ChangeDetectionStrategy, DerivativeDispositionPolicy, Clock)
     */
    public ResourceLifecycleCoordinator(MediatedResourceAccess mediatedAccess,
                                         ActorIdentity actor,
                                         ContentInspector contentInspector,
                                         ResourcePolicy policy,
                                         InMemoryResourceArchive archive,
                                         LexicalIndex lexicalIndex) {
        this(mediatedAccess, actor, contentInspector, policy, archive, lexicalIndex, null);
    }

    /**
     * Creates a coordinator over the records of an in-memory archive with optional chunking,
     * the Tamias digest change detection, the Tamias disposition policy and the UTC clock.
     */
    public ResourceLifecycleCoordinator(MediatedResourceAccess mediatedAccess,
                                         ActorIdentity actor,
                                         ContentInspector contentInspector,
                                         ResourcePolicy policy,
                                         InMemoryResourceArchive archive,
                                         LexicalIndex lexicalIndex,
                                         LexicalChunker lexicalChunker) {
        this(mediatedAccess, actor, contentInspector, policy, recordsOf(archive), lexicalIndex, lexicalChunker,
                new DigestChangeDetection(), new DerivativeDispositionPolicy(), Clock.systemUTC());
    }

    /**
     * Creates a coordinator.
     *
     * @param mediatedAccess    the mediated bronze access contract (archive counter)
     * @param actor             the identity under which this lifecycle requests resources
     * @param contentInspector  content inspector port
     * @param policy            indexing policy
     * @param records           the resource records (#33), the only truth about versions
     * @param lexicalIndex      lexical index
     * @param lexicalChunker    optional chunker; if non-null, text blocks are chunked before indexing
     * @param changeDetection   Tamias change detection (#5)
     * @param dispositionPolicy Tamias derivative disposition (#5)
     * @param clock             clock for indexing and removal times
     */
    public ResourceLifecycleCoordinator(MediatedResourceAccess mediatedAccess,
                                         ActorIdentity actor,
                                         ContentInspector contentInspector,
                                         ResourcePolicy policy,
                                         ResourceArchiveRepository records,
                                         LexicalIndex lexicalIndex,
                                         LexicalChunker lexicalChunker,
                                         ChangeDetectionStrategy changeDetection,
                                         DerivativeDispositionPolicy dispositionPolicy,
                                         Clock clock) {
        if (mediatedAccess == null) throw new IllegalArgumentException("mediatedAccess must not be null");
        if (actor == null) throw new IllegalArgumentException("actor must not be null");
        if (contentInspector == null) throw new IllegalArgumentException("contentInspector must not be null");
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        if (records == null) throw new IllegalArgumentException("records must not be null");
        if (lexicalIndex == null) throw new IllegalArgumentException("lexicalIndex must not be null");
        if (changeDetection == null) throw new IllegalArgumentException("changeDetection must not be null");
        if (dispositionPolicy == null) throw new IllegalArgumentException("dispositionPolicy must not be null");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.mediatedAccess = mediatedAccess;
        this.actor = actor;
        this.contentInspector = contentInspector;
        this.policy = policy;
        this.records = records;
        this.lexicalIndex = lexicalIndex;
        this.lexicalChunker = lexicalChunker;
        this.changeDetection = changeDetection;
        this.dispositionPolicy = dispositionPolicy;
        this.clock = clock;
    }

    private static ResourceArchiveRepository recordsOf(InMemoryResourceArchive archive) {
        if (archive == null) throw new IllegalArgumentException("archive must not be null");
        return archive.records();
    }

    /**
     * Processes a single resource through the full pipeline and records every executed step
     * (#10 Slice 4).
     *
     * @param ref the resource reference to process
     * @return the immutable processing record
     */
    public ProcessingResult process(VirtualResourceRef ref) {
        Execution run = new Execution(ref);
        if (ref == null) {
            return run.fail(ResourceProcessingFailure.Reason.INVALID_REQUEST, "Resource reference must not be null");
        }

        // 1. Indexing policy before acquisition (scheme, include/exclude patterns, size unknown)
        PolicyReason preAcquisition = policy.evaluate(ref, ResourcePolicy.SIZE_UNKNOWN);
        if (preAcquisition.decision() == AcceptanceDecision.DENY) {
            run.stopped(ResourceProcessingStepType.INDEXING_POLICY_BEFORE_ACQUISITION, preAcquisition.reason());
            return cleanupAfterStop(run, ResourceProcessingOutcome.DENIED, preAcquisition.reason(), null);
        }
        run.completed(ResourceProcessingStepType.INDEXING_POLICY_BEFORE_ACQUISITION, preAcquisition.reason());

        // 2. Source metadata: reject a known oversized resource before its payload is acquired
        run.at(ResourceProcessingStepType.SOURCE_METADATA);
        MediatedResult<BronzeMetadata> metadata;
        try {
            metadata = mediatedAccess.readMetadata(request(ref, ResourceOperation.READ_METADATA));
        } catch (RuntimeException e) {
            return run.fail(ResourceProcessingFailure.Reason.MEDIATED_ACCESS_ERROR,
                    "Mediated metadata read failed: " + e.getMessage());
        }
        if (metadata != null && metadata.isSuccess() && metadata.value() != null
                && metadata.value().sizeBytes() >= 0) {
            long size = metadata.value().sizeBytes();
            run.completed(ResourceProcessingStepType.SOURCE_METADATA, "size " + size);
            PolicyReason sizeCheck = policy.evaluate(ref, size);
            if (sizeCheck.decision() == AcceptanceDecision.DENY) {
                run.stopped(ResourceProcessingStepType.INDEXING_POLICY_WITH_SIZE, sizeCheck.reason());
                mediatedAccess.invalidatePayload(ref.uri());
                return cleanupAfterStop(run, ResourceProcessingOutcome.DENIED, sizeCheck.reason(), null);
            }
        } else if (failureOf(metadata) == MediatedResult.Failure.SOURCE_ABSENT) {
            run.stopped(ResourceProcessingStepType.SOURCE_METADATA, "source reports the resource absent");
            return sourceAbsent(run, records.findByRef(ref));
        } else if (failureOf(metadata) == MediatedResult.Failure.AUTHENTICATION_CANCELLED) {
            return failedAcquisition(run, ResourceProcessingStepType.SOURCE_METADATA, metadata);
        } else {
            run.completed(ResourceProcessingStepType.SOURCE_METADATA, "size unknown");
        }

        // 3. Mediated read; the counter refreshes from the source when Tamias permits it
        run.at(ResourceProcessingStepType.MEDIATED_ACQUISITION);
        MediatedResult<BronzeContent> access;
        try {
            access = mediatedAccess.refreshContent(request(ref, ResourceOperation.READ_CONTENT));
        } catch (RuntimeException e) {
            return run.fail(ResourceProcessingFailure.Reason.MEDIATED_ACCESS_ERROR,
                    "Mediated access failed: " + e.getMessage());
        }
        if (access == null) {
            return run.fail(ResourceProcessingFailure.Reason.MEDIATED_ACCESS_ERROR, "Mediated access returned no result");
        }
        if (!access.isSuccess()) {
            // Any withheld result that carries a Tamias decision is a DENIED outcome. This includes
            // ALLOW_CACHED_ONLY returned for FETCH_EXTERNAL on a cache miss, which the counter wraps
            // as a withheld result although the decision type itself counts as "allowed".
            if (access.decision() != null) {
                String reason = describe(access.decision());
                run.stopped(ResourceProcessingStepType.MEDIATED_ACQUISITION, reason);
                return accessWithheld(run, reason);
            }
            if (failureOf(access) == MediatedResult.Failure.SOURCE_ABSENT) {
                run.stopped(ResourceProcessingStepType.MEDIATED_ACQUISITION, "source reports the resource absent");
                return sourceAbsent(run, records.findByRef(ref));
            }
            return failedAcquisition(run, ResourceProcessingStepType.MEDIATED_ACQUISITION, access);
        }
        BronzeContent bronze = access.value();
        if (bronze == null) {
            return run.fail(ResourceProcessingFailure.Reason.MEDIATED_ACCESS_ERROR, "Mediated access returned no content");
        }
        run.completed(ResourceProcessingStepType.MEDIATED_ACQUISITION, null);

        byte[] bytes = bronze.content();

        // 4. Indexing policy with the actual size; a rejected payload must not stay cached
        PolicyReason policyResult = policy.evaluate(ref, bytes.length);
        if (policyResult.decision() == AcceptanceDecision.DENY) {
            run.stopped(ResourceProcessingStepType.INDEXING_POLICY_WITH_SIZE, policyResult.reason());
            mediatedAccess.invalidatePayload(ref.uri());
            return cleanupAfterStop(run, ResourceProcessingOutcome.DENIED, policyResult.reason(), null);
        }
        run.completed(ResourceProcessingStepType.INDEXING_POLICY_WITH_SIZE, policyResult.reason());

        // 5. Tamias change detection and disposition on the record projection (#5, #33)
        ArchivedResource record = records.findByRef(ref);
        if (record != null && record.isRemovedAtSource()
                && bronze.fetchedAtMillis() < record.sourceRemoval().observedAtMillis()) {
            // A payload fetched before the recorded removal does not prove that the resource exists
            // again, so the recorded removal stands
            return sourceAbsent(run, record);
        }
        ResourceDigest digest = bronze.digest();
        SourceObservation observation = record == null
                ? SourceObservation.presentWithoutRecord()
                : SourceObservation.present(record.latestObservedVersion().digest().equals(digest)
                        ? ContentComparison.SAME_AS_LATEST_OBSERVED
                        : ContentComparison.DIFFERS_FROM_LATEST_OBSERVED);
        DerivativeDisposition disposition = dispositionPolicy.decide(
                changeDetection.detect(factsOf(record), observation));
        ArchivedResource observed = record == null
                ? ArchivedResource.firstObservation(ref, digest, bronze.fetchedAtMillis())
                : record.observe(digest, bronze.fetchedAtMillis());
        if (observed != record) {
            records.save(observed);
        }
        if (!disposition.requiresIndexing()) {
            run.stopped(ResourceProcessingStepType.CHANGE_DETECTION, describe(disposition));
            return run.finish(ResourceProcessingOutcome.UNCHANGED, "content unchanged", null);
        }
        run.completed(ResourceProcessingStepType.CHANGE_DETECTION, describe(disposition));

        // 6. Detect and extract content
        String filenameHint = filenameHint(ref.uri());
        run.at(ResourceProcessingStepType.CONTENT_INSPECTION);
        InspectionResult inspection = contentInspector.inspect(ref, bytes, filenameHint);
        if (!inspection.isSuccess()) {
            return run.fail(ResourceProcessingFailure.Reason.INSPECTION_FAILED, inspection.errorMessage());
        }
        LexicalDocument document = lexicalDocument(ref, filenameHint, inspection);
        if (document == null) {
            // Extraction succeeded but no text-bearing blocks remain
            String reason = "No indexable text content after extraction";
            run.stopped(ResourceProcessingStepType.CONTENT_INSPECTION, reason);
            return cleanupAfterStop(run, ResourceProcessingOutcome.NO_EXTRACTABLE_CONTENT, reason,
                    new ResourceProcessingFailure(ResourceProcessingFailure.Reason.NO_INDEXABLE_TEXT, reason));
        }
        run.completed(ResourceProcessingStepType.CONTENT_INSPECTION, inspection.mimeType());

        // 7. Index via anagraphai
        run.at(ResourceProcessingStepType.LEXICAL_INDEXING);
        try {
            lexicalIndex.index(document);
            lexicalIndex.commit();
        } catch (IOException e) {
            return run.fail(ResourceProcessingFailure.Reason.INDEXING_FAILED, "Indexing failed: " + e.getMessage());
        }
        run.completed(ResourceProcessingStepType.LEXICAL_INDEXING, null);

        // 8. Record the indexed version in the resource record
        records.save(observed.markIndexed(observed.latestObservedVersion(), clock.millis()));
        run.completed(ResourceProcessingStepType.RECORD_UPDATE,
                "indexed version #" + observed.latestObservedVersion().sequence());

        return run.finish(ResourceProcessingOutcome.INDEXED, "indexed successfully", null);
    }

    private ResourceAccessRequest request(VirtualResourceRef ref, ResourceOperation operation) {
        return new ResourceAccessRequest(actor, ref.uri(), operation, LIFECYCLE_PURPOSE);
    }

    /**
     * Maps a resource record to the Tamias fact projection (#5). Returns {@code null} for an
     * unknown resource.
     */
    static ResourceRecordFacts factsOf(ArchivedResource record) {
        if (record == null) {
            return null;
        }
        long indexed = record.isIndexed()
                ? record.indexedVersion().version().sequence()
                : ResourceRecordFacts.NOT_INDEXED;
        return new ResourceRecordFacts(record.latestObservedVersion().sequence(), indexed,
                record.isRemovedAtSource());
    }

    /**
     * Ends a run whose content Tamias withheld. Derived state stays, unless the record carries an
     * explicit removal: then the Tamias disposition for that removal is executed.
     */
    private ProcessingResult accessWithheld(Execution run, String reason) {
        ArchivedResource record = records.findByRef(run.ref);
        if (record == null || !record.isRemovedAtSource()) {
            return run.finish(ResourceProcessingOutcome.DENIED, reason, null);
        }
        DerivativeDisposition disposition = dispositionPolicy.decide(
                changeDetection.detect(factsOf(record), SourceObservation.absent()));
        run.completed(ResourceProcessingStepType.CHANGE_DETECTION, describe(disposition));
        return executeRemoval(run, record, disposition, ResourceProcessingOutcome.DENIED, reason);
    }

    /** Executes the Tamias disposition for a resource the source reports as absent. */
    private ProcessingResult sourceAbsent(Execution run, ArchivedResource record) {
        ChangeDecision change = changeDetection.detect(factsOf(record), SourceObservation.absent());
        DerivativeDisposition disposition = dispositionPolicy.decide(change);
        if (record == null) {
            if (disposition.cacheAction() == CacheAction.INVALIDATE) {
                mediatedAccess.invalidatePayload(run.ref.uri());
            }
            run.completed(ResourceProcessingStepType.CHANGE_DETECTION, describe(disposition));
            return run.finish(ResourceProcessingOutcome.FAILED, "Resource not found at its source",
                    new ResourceProcessingFailure(ResourceProcessingFailure.Reason.SOURCE_NOT_FOUND,
                            "Resource not found at its source"));
        }
        run.completed(ResourceProcessingStepType.CHANGE_DETECTION, describe(disposition));
        ArchivedResource updated = record;
        if (change.reasonCode() == ChangeReasonCode.REMOVAL_OBSERVED) {
            updated = record.markRemovedAtSource(clock.millis());
            records.save(updated);
            run.completed(ResourceProcessingStepType.RECORD_UPDATE, "removal at source recorded");
        }
        return executeRemoval(run, updated, disposition, ResourceProcessingOutcome.REMOVED, "removed at source");
    }

    /** Invalidates the payload and withdraws the index entry as the disposition requires. */
    private ProcessingResult executeRemoval(Execution run, ArchivedResource record, DerivativeDisposition disposition,
                                            ResourceProcessingOutcome outcome, String message) {
        if (disposition.cacheAction() == CacheAction.INVALIDATE) {
            mediatedAccess.invalidatePayload(run.ref.uri());
        }
        if (!disposition.requiresWithdrawal()) {
            return run.finish(outcome, message, null);
        }
        try {
            lexicalIndex.remove(run.ref);
            lexicalIndex.commit();
        } catch (IOException e) {
            return cleanupFailed(run, e);
        }
        records.save(record.withdrawIndexedVersion());
        run.completed(ResourceProcessingStepType.DERIVED_STATE_CLEANUP, "index entry withdrawn");
        return run.finish(outcome, message + "; index entry withdrawn", null);
    }

    /** Builds the lexical document, or returns {@code null} if no text-bearing block remains. */
    private LexicalDocument lexicalDocument(VirtualResourceRef ref, String filenameHint, InspectionResult inspection) {
        LexicalDocument.Builder docBuilder = LexicalDocument.builder(ref)
                .title(filenameHint)
                .contentType(inspection.mimeType());

        int chunkIndex = 0;
        for (String text : inspection.textBlocks()) {
            if (text != null && !text.isEmpty()) {
                if (lexicalChunker != null) {
                    // Use sentence-aware, token-budgeted chunking
                    List<LexicalChunk> textChunks = lexicalChunker.chunk(text);
                    for (LexicalChunk tc : textChunks) {
                        docBuilder.addChunk(new LexicalChunk(chunkIndex, tc.text()));
                        chunkIndex++;
                    }
                } else {
                    docBuilder.addChunk(new LexicalChunk(chunkIndex, text));
                    chunkIndex++;
                }
            }
        }
        return chunkIndex == 0 ? null : docBuilder.build();
    }

    private static MediatedResult.Failure failureOf(MediatedResult<?> result) {
        if (result == null || result.isSuccess() || result.decision() != null) {
            return null;
        }
        return result.failure() == null ? MediatedResult.Failure.ACQUISITION_FAILED : result.failure();
    }

    private static ProcessingResult failedAcquisition(Execution run, ResourceProcessingStepType step,
                                                      MediatedResult<?> access) {
        MediatedResult.Failure kind = failureOf(access);
        if (kind == null) {
            kind = MediatedResult.Failure.ACQUISITION_FAILED;
        }
        switch (kind) {
            case AUTHENTICATION_CANCELLED:
                run.stopped(step, "credential request cancelled");
                return run.finish(ResourceProcessingOutcome.CANCELLED, "Credential request cancelled",
                        new ResourceProcessingFailure(ResourceProcessingFailure.Reason.AUTHENTICATION_CANCELLED,
                                access.errorMessage()));
            case AUTHENTICATION_UNAVAILABLE:
                return run.fail(ResourceProcessingFailure.Reason.AUTHENTICATION_UNAVAILABLE,
                        "No credential available: " + access.errorMessage());
            case AUTHENTICATION_FAILED:
                return run.fail(ResourceProcessingFailure.Reason.AUTHENTICATION_FAILED,
                        "Authentication failed: " + access.errorMessage());
            case ACQUISITION_FAILED:
            default:
                return run.fail(ResourceProcessingFailure.Reason.ACQUISITION_FAILED,
                        "Acquisition failed: " + access.errorMessage());
        }
    }

    /**
     * Removes stale lexical entries and withdraws the indexed-version fact after a lifecycle
     * decision; the resource record and its history are kept (#33).
     */
    private ProcessingResult cleanupAfterStop(Execution run, ResourceProcessingOutcome outcome, String message,
                                              ResourceProcessingFailure failure) {
        VirtualResourceRef ref = run.ref;
        try {
            lexicalIndex.remove(ref);
            lexicalIndex.commit();
        } catch (IOException e) {
            return cleanupFailed(run, e);
        }
        ArchivedResource record = records.findByRef(ref);
        if (record != null && record.isIndexed()) {
            records.save(record.withdrawIndexedVersion());
        }
        run.completed(ResourceProcessingStepType.DERIVED_STATE_CLEANUP, null);
        return run.finish(outcome, message, failure);
    }

    private static ProcessingResult cleanupFailed(Execution run, IOException e) {
        run.failed(ResourceProcessingStepType.DERIVED_STATE_CLEANUP, e.getMessage());
        return run.finish(ResourceProcessingOutcome.FAILED, "Index cleanup failed: " + e.getMessage(),
                new ResourceProcessingFailure(ResourceProcessingFailure.Reason.CLEANUP_FAILED, e.getMessage()));
    }

    private static String describe(DerivativeDisposition disposition) {
        return disposition.change().reasonCode() + ": cache " + disposition.cacheAction()
                + ", index " + disposition.indexAction() + " (" + disposition.indexReason() + ")";
    }

    /** Collects the executed steps of one resource and produces the immutable result. */
    private static final class Execution {
        private final VirtualResourceRef ref;
        private final List<ResourceProcessingStep> steps = new ArrayList<ResourceProcessingStep>();
        private ResourceProcessingStepType current;

        Execution(VirtualResourceRef ref) {
            this.ref = ref;
        }

        void at(ResourceProcessingStepType step) {
            current = step;
        }

        void completed(ResourceProcessingStepType step, String detail) {
            steps.add(ResourceProcessingStep.completed(step, detail));
            current = null;
        }

        void stopped(ResourceProcessingStepType step, String detail) {
            steps.add(ResourceProcessingStep.stopped(step, detail));
            current = null;
        }

        void failed(ResourceProcessingStepType step, String detail) {
            steps.add(ResourceProcessingStep.failed(step, detail));
            current = null;
        }

        ProcessingResult fail(ResourceProcessingFailure.Reason reason, String message) {
            if (current != null) {
                failed(current, reason.name());
            }
            return finish(ResourceProcessingOutcome.FAILED, message, new ResourceProcessingFailure(reason, message));
        }

        ProcessingResult finish(ResourceProcessingOutcome outcome, String message, ResourceProcessingFailure failure) {
            return ProcessingResult.of(ref, outcome, message, failure, steps);
        }
    }

    private static String describe(ResourceAccessDecision decision) {
        if (decision == null) {
            return "access withheld without decision";
        }
        StringBuilder text = new StringBuilder("access ")
                .append(decision.type())
                .append(" (")
                .append(decision.reasonCode())
                .append(")");
        if (decision.explanation() != null) {
            text.append(": ").append(decision.explanation());
        }
        return text.toString();
    }

    /**
     * Derives a filename hint from the last path segment of the resource address.
     *
     * <p>Bronze content carries no name yet (#33 introduces resource records with metadata);
     * until then the address is the only name source available to the lifecycle.
     *
     * @param uri the resource address
     * @return the last path segment, or {@code null} if none can be derived
     */
    static String filenameHint(BookmarkUri uri) {
        if (uri == null) {
            return null;
        }
        URI standard = uri.toURI();
        String path = standard != null && standard.getPath() != null
                ? standard.getPath()
                : uri.schemeSpecificPart();
        if (path == null) {
            return null;
        }
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        int start = path.lastIndexOf('/', end - 1) + 1;
        String name = path.substring(start, end);
        return name.isEmpty() ? null : name;
    }
}
