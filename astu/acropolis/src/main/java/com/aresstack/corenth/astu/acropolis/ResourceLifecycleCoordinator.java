package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceDigest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceSnapshot;
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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the resource lifecycle pipeline for a single resource.
 *
 * <p>Resources are obtained exclusively through the mediated bronze archive counter
 * ({@link MediatedResourceAccess}, implemented by Chalcotheca's {@code MediatedResourceService}).
 * The coordinator never sees connectors, the acquisition port or secrets; Tamias decides for
 * every read whether the lifecycle actor may receive the content and whether external
 * acquisition is permitted. There is no direct provider or connector path (#10 Slice 1).
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@code tamias} {@link ResourcePolicy} — indexing rules before acquisition (size unknown)</li>
 *   <li>{@link MediatedResourceAccess} — mediated read: Tamias access decision, cached or
 *       acquired bronze content</li>
 *   <li>{@code tamias} {@link ResourcePolicy} — indexing rules with the actual size</li>
 *   <li>{@code chalcotheca} — change detection against the indexed version; unchanged content
 *       is neither extracted nor indexed again</li>
 *   <li>{@link ContentInspector} — detect and extract</li>
 *   <li>{@code anagraphai} — lexical indexing</li>
 *   <li>{@code chalcotheca} — record the indexed version</li>
 * </ol>
 *
 * <p>Every call records the executed steps in {@link ResourceProcessingPlan#standard()} order,
 * the {@link ResourceProcessingOutcome} and a typed {@link ResourceProcessingFailure}
 * (#10 Slice 4); {@link ResourceProcessingRunner} records runs over several resources.
 * Authentication cancellation is {@code CANCELLED}, missing credentials and authentication
 * failure are {@code FAILED} with their own reason codes (#10 Slice 3).
 *
 * <p>Outcome semantics for the mediated read:
 * <ul>
 *   <li>allowed, content present (fresh or cached): processing continues;</li>
 *   <li>Tamias withholds the content, i.e. the result carries a decision but no payload
 *       ({@code DENY}, {@code ALLOW_CACHED_ONLY} without cached content on either evaluation,
 *       {@code REQUIRE_AUTH}, {@code REQUIRE_SOURCE_CHECK}): {@code DENIED} with the decision
 *       in the message. Access denials are never lifecycle decisions: they do <em>not</em>
 *       remove derived state (lexical index, lifecycle snapshot), not even for resource-level
 *       reason codes such as {@code BLACKLISTED}. Withdrawing an already indexed resource is
 *       an explicit operation decided by Tamias (#5) and executed by the lifecycle (#10) on
 *       top of the #33 resource records; {@code REQUIRE_AUTH} becomes an Adyton-backed
 *       preparation step in #10 Slice 3;</li>
 *   <li>acquisition error (no decision): {@code FAILED}.</li>
 * </ul>
 * In contrast, an indexing-policy {@code DENY} is a lifecycle decision for this resource and
 * removes stale index entries and withdraws the archive's indexed-version fact (the resource
 * record and its version history are kept, #33).
 *
 * <p>Known gaps that are deliberately left to later slices:
 * <ul>
 *   <li>No pre-acquisition size probe exists on the mediated contract. Before Slice 1 the
 *       skeleton denied oversized files before fetching; now the indexing policy is evaluated
 *       first with {@link ResourcePolicy#SIZE_UNKNOWN} (scheme and patterns only), the counter
 *       acquires the content, and size limits are enforced afterwards. Oversized content is
 *       therefore acquired and retained in the counter's cache for the lifetime of the service
 *       instance; the lifecycle cannot evict it ({@code deleteEntry} is not on the contract).
 *       A {@code READ_METADATA} operation on the contract and the acquisition port belongs to
 *       #5/#10 (#33 already records the facts; the policy is #5, contract wiring and execution
 *       are #10).</li>
 *   <li>The counter's bronze caches have no invalidation. Before Slice 1 every run re-read the
 *       source and a changed file was re-indexed; through the counter a changed source is
 *       served from the cache within one service instance and reported as {@code UNCHANGED}
 *       until #5 decides invalidation and #10 executes it (#33 only records the facts).</li>
 * </ul>
 */
public final class ResourceLifecycleCoordinator {

    /** Purpose recorded on every mediated request issued by this lifecycle. */
    public static final String LIFECYCLE_PURPOSE = "acropolis-lifecycle-indexing";

    private final MediatedResourceAccess mediatedAccess;
    private final ActorIdentity actor;
    private final ContentInspector contentInspector;
    private final ResourcePolicy policy;
    private final ResourceArchive archive;
    private final LexicalIndex lexicalIndex;
    private final LexicalChunker lexicalChunker;

    public ResourceLifecycleCoordinator(MediatedResourceAccess mediatedAccess,
                                         ActorIdentity actor,
                                         ContentInspector contentInspector,
                                         ResourcePolicy policy,
                                         ResourceArchive archive,
                                         LexicalIndex lexicalIndex) {
        this(mediatedAccess, actor, contentInspector, policy, archive, lexicalIndex, null);
    }

    /**
     * Creates a coordinator with optional lexical chunking support.
     *
     * @param mediatedAccess the mediated bronze access contract (archive counter)
     * @param actor the identity under which this lifecycle requests resources
     * @param contentInspector content inspector port
     * @param policy indexing policy
     * @param archive lifecycle snapshot archive
     * @param lexicalIndex lexical index
     * @param lexicalChunker optional chunker; if non-null, text blocks are chunked before indexing
     */
    public ResourceLifecycleCoordinator(MediatedResourceAccess mediatedAccess,
                                         ActorIdentity actor,
                                         ContentInspector contentInspector,
                                         ResourcePolicy policy,
                                         ResourceArchive archive,
                                         LexicalIndex lexicalIndex,
                                         LexicalChunker lexicalChunker) {
        if (mediatedAccess == null) throw new IllegalArgumentException("mediatedAccess must not be null");
        if (actor == null) throw new IllegalArgumentException("actor must not be null");
        if (contentInspector == null) throw new IllegalArgumentException("contentInspector must not be null");
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        if (archive == null) throw new IllegalArgumentException("archive must not be null");
        if (lexicalIndex == null) throw new IllegalArgumentException("lexicalIndex must not be null");
        this.mediatedAccess = mediatedAccess;
        this.actor = actor;
        this.contentInspector = contentInspector;
        this.policy = policy;
        this.archive = archive;
        this.lexicalIndex = lexicalIndex;
        this.lexicalChunker = lexicalChunker;
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

        // 1. Indexing policy before acquisition (scheme, include/exclude patterns).
        //    The size is unknown at this point; size limits are enforced again after acquisition.
        PolicyReason preAcquisition = policy.evaluate(ref, ResourcePolicy.SIZE_UNKNOWN);
        if (preAcquisition.decision() == AcceptanceDecision.DENY) {
            run.stopped(ResourceProcessingStepType.INDEXING_POLICY_BEFORE_ACQUISITION, preAcquisition.reason());
            return cleanupAfterStop(run, ResourceProcessingOutcome.DENIED, preAcquisition.reason(), null);
        }
        run.completed(ResourceProcessingStepType.INDEXING_POLICY_BEFORE_ACQUISITION, preAcquisition.reason());

        // 2. Mediated acquisition through the archive counter (Tamias decides, Holkas stays hidden)
        run.at(ResourceProcessingStepType.MEDIATED_ACQUISITION);
        MediatedResult<BronzeContent> access;
        try {
            access = mediatedAccess.readContent(new ResourceAccessRequest(
                    actor, ref.uri(), ResourceOperation.READ_CONTENT, LIFECYCLE_PURPOSE));
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
                return run.finish(ResourceProcessingOutcome.DENIED, reason, null);
            }
            return failedAcquisition(run, access);
        }
        BronzeContent bronze = access.value();
        if (bronze == null) {
            return run.fail(ResourceProcessingFailure.Reason.MEDIATED_ACCESS_ERROR, "Mediated access returned no content");
        }
        run.completed(ResourceProcessingStepType.MEDIATED_ACQUISITION, null);

        byte[] bytes = bronze.content();

        // 3. Indexing policy with the actual size (tamias)
        PolicyReason policyResult = policy.evaluate(ref, bytes.length);
        if (policyResult.decision() == AcceptanceDecision.DENY) {
            run.stopped(ResourceProcessingStepType.INDEXING_POLICY_WITH_SIZE, policyResult.reason());
            return cleanupAfterStop(run, ResourceProcessingOutcome.DENIED, policyResult.reason(), null);
        }
        run.completed(ResourceProcessingStepType.INDEXING_POLICY_WITH_SIZE, policyResult.reason());

        // 4. Unchanged content needs neither extraction nor an index write (chalcotheca facts)
        ResourceDigest digest = bronze.digest();
        if (!archive.hasChanged(ref, digest)) {
            run.stopped(ResourceProcessingStepType.CHANGE_DETECTION, "indexed version is current");
            return run.finish(ResourceProcessingOutcome.UNCHANGED, "content unchanged", null);
        }
        run.completed(ResourceProcessingStepType.CHANGE_DETECTION, "indexing required");

        // 5. Detect and extract content
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

        // 6. Index via anagraphai
        run.at(ResourceProcessingStepType.LEXICAL_INDEXING);
        try {
            lexicalIndex.index(document);
            lexicalIndex.commit();
        } catch (IOException e) {
            return run.fail(ResourceProcessingFailure.Reason.INDEXING_FAILED, "Indexing failed: " + e.getMessage());
        }
        run.completed(ResourceProcessingStepType.LEXICAL_INDEXING, null);

        // 7. Record the indexed version in the resource record
        archive.store(new ResourceSnapshot(ref, digest, System.currentTimeMillis()));
        run.completed(ResourceProcessingStepType.RECORD_UPDATE, null);

        return run.finish(ResourceProcessingOutcome.INDEXED, "indexed successfully", null);
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

    private static ProcessingResult failedAcquisition(Execution run, MediatedResult<BronzeContent> access) {
        MediatedResult.Failure kind = access.failure() == null
                ? MediatedResult.Failure.ACQUISITION_FAILED : access.failure();
        switch (kind) {
            case AUTHENTICATION_CANCELLED:
                run.stopped(ResourceProcessingStepType.MEDIATED_ACQUISITION, "credential request cancelled");
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
            archive.remove(ref);
        } catch (IOException e) {
            run.failed(ResourceProcessingStepType.DERIVED_STATE_CLEANUP, e.getMessage());
            return run.finish(ResourceProcessingOutcome.FAILED, "Index cleanup failed: " + e.getMessage(),
                    new ResourceProcessingFailure(ResourceProcessingFailure.Reason.CLEANUP_FAILED, e.getMessage()));
        }
        run.completed(ResourceProcessingStepType.DERIVED_STATE_CLEANUP, null);
        return run.finish(outcome, message, failure);
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
