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
 *   <li>{@link ContentInspector} — detect and extract</li>
 *   <li>{@code anagraphai} — lexical indexing</li>
 *   <li>{@code chalcotheca} — snapshot for change detection</li>
 * </ol>
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
 *       #5/#33.</li>
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
     * Processes a single resource through the full pipeline.
     *
     * @param ref the resource reference to process
     * @return the processing result
     */
    public ProcessingResult process(VirtualResourceRef ref) {
        if (ref == null) {
            return ProcessingResult.failed(null, "Resource reference must not be null");
        }

        // 1. Indexing policy before acquisition (scheme, include/exclude patterns).
        //    The size is unknown at this point; size limits are enforced again after acquisition.
        PolicyReason preAcquisition = policy.evaluate(ref, ResourcePolicy.SIZE_UNKNOWN);
        if (preAcquisition.decision() == AcceptanceDecision.DENY) {
            return cleanupAndReturn(ProcessingResult.denied(ref, preAcquisition.reason()));
        }

        // 2. Mediated acquisition through the archive counter (Tamias decides, Holkas stays hidden)
        MediatedResult<BronzeContent> access;
        try {
            access = mediatedAccess.readContent(new ResourceAccessRequest(
                    actor, ref.uri(), ResourceOperation.READ_CONTENT, LIFECYCLE_PURPOSE));
        } catch (RuntimeException e) {
            return ProcessingResult.failed(ref, "Mediated access failed: " + e.getMessage());
        }
        if (access == null) {
            return ProcessingResult.failed(ref, "Mediated access returned no result");
        }
        if (!access.isSuccess()) {
            // Any withheld result that carries a Tamias decision is a DENIED outcome. This includes
            // ALLOW_CACHED_ONLY returned for FETCH_EXTERNAL on a cache miss, which the counter wraps
            // as a withheld result although the decision type itself counts as "allowed".
            if (access.decision() != null) {
                return ProcessingResult.denied(ref, describe(access.decision()));
            }
            return ProcessingResult.failed(ref, "Acquisition failed: " + access.errorMessage());
        }
        BronzeContent bronze = access.value();
        if (bronze == null) {
            return ProcessingResult.failed(ref, "Mediated access returned no content");
        }

        byte[] bytes = bronze.content();

        // 3. Indexing policy with the actual size (tamias)
        PolicyReason policyResult = policy.evaluate(ref, bytes.length);
        if (policyResult.decision() == AcceptanceDecision.DENY) {
            return cleanupAndReturn(ProcessingResult.denied(ref, policyResult.reason()));
        }

        // 4. The digest travels with the bronze content (chalcotheca)
        ResourceDigest digest = bronze.digest();
        String filenameHint = filenameHint(ref.uri());

        // 5. Detect and extract content
        InspectionResult inspection = contentInspector.inspect(ref, bytes, filenameHint);
        if (!inspection.isSuccess()) {
            return ProcessingResult.failed(ref, inspection.errorMessage());
        }

        // 6. Index via anagraphai — skip blocks with null/empty text
        List<String> textBlocks = inspection.textBlocks();
        LexicalDocument.Builder docBuilder = LexicalDocument.builder(ref)
                .title(filenameHint)
                .contentType(inspection.mimeType());

        int chunkIndex = 0;
        for (String text : textBlocks) {
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

        if (chunkIndex == 0) {
            // Extraction succeeded but no text-bearing blocks remain
            return cleanupAndReturn(
                    ProcessingResult.failed(ref, "No indexable text content after extraction"));
        }

        // 7. Check archive for unchanged content
        if (!archive.hasChanged(ref, digest)) {
            return ProcessingResult.unchanged(ref);
        }

        try {
            lexicalIndex.index(docBuilder.build());
            lexicalIndex.commit();
        } catch (IOException e) {
            return ProcessingResult.failed(ref, "Indexing failed: " + e.getMessage());
        }

        // 8. Record snapshot in archive
        archive.store(new ResourceSnapshot(ref, digest, System.currentTimeMillis()));

        return ProcessingResult.indexed(ref);
    }

    private ProcessingResult cleanupAndReturn(ProcessingResult result) {
        try {
            lexicalIndex.remove(result.ref());
            lexicalIndex.commit();
            archive.remove(result.ref());
            return result;
        } catch (IOException e) {
            return ProcessingResult.failed(result.ref(),
                    "Index cleanup failed: " + e.getMessage());
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
