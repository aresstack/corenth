package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ContentHasher;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceDigest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceSnapshot;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LexicalChunker;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AcceptanceDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PolicyReason;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;

import java.io.IOException;
import java.util.List;

/**
 * Orchestrates the walking skeleton pipeline for resource lifecycle processing.
 *
 * <p>Depends only on inward-facing ports ({@link RawResourceProvider},
 * {@link ContentInspector}) and core inner modules. Outer adapter implementations
 * (holkas, deigma) are wired at the composition layer, not compiled against here.
 *
 * <p>Connects:
 * <ol>
 *   <li>{@link RawResourceProvider} — fetch raw content</li>
 *   <li>{@link ContentInspector} — detect and extract</li>
 *   <li>{@code tamias} — policy decision</li>
 *   <li>{@code chalcotheca} — snapshot/cache</li>
 *   <li>{@code anagraphai} — lexical indexing</li>
 * </ol>
 */
public final class ResourceLifecycleCoordinator {

    private final RawResourceProvider resourceProvider;
    private final ContentInspector contentInspector;
    private final ResourcePolicy policy;
    private final ResourceArchive archive;
    private final LexicalIndex lexicalIndex;
    private final LexicalChunker lexicalChunker;

    public ResourceLifecycleCoordinator(RawResourceProvider resourceProvider,
                                         ContentInspector contentInspector,
                                         ResourcePolicy policy,
                                         ResourceArchive archive,
                                         LexicalIndex lexicalIndex) {
        this(resourceProvider, contentInspector, policy, archive, lexicalIndex, null);
    }

    /**
     * Creates a coordinator with optional lexical chunking support.
     *
     * @param resourceProvider resource provider port
     * @param contentInspector content inspector port
     * @param policy resource policy
     * @param archive resource archive
     * @param lexicalIndex lexical index
     * @param lexicalChunker optional chunker; if non-null, text blocks are chunked before indexing
     */
    public ResourceLifecycleCoordinator(RawResourceProvider resourceProvider,
                                         ContentInspector contentInspector,
                                         ResourcePolicy policy,
                                         ResourceArchive archive,
                                         LexicalIndex lexicalIndex,
                                         LexicalChunker lexicalChunker) {
        if (resourceProvider == null) throw new IllegalArgumentException("resourceProvider must not be null");
        if (contentInspector == null) throw new IllegalArgumentException("contentInspector must not be null");
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        if (archive == null) throw new IllegalArgumentException("archive must not be null");
        if (lexicalIndex == null) throw new IllegalArgumentException("lexicalIndex must not be null");
        this.resourceProvider = resourceProvider;
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

        Long sizeHint = null;
        try {
            sizeHint = resourceProvider.probeSizeBytes(ref);
        } catch (IOException ignored) {
            // Best-effort preflight only; continue with normal fetch path.
        } catch (IllegalArgumentException e) {
            return ProcessingResult.failed(ref, "Invalid resource reference: " + e.getMessage());
        }
        if (sizeHint != null) {
            PolicyReason preFetchPolicyResult = policy.evaluate(ref, sizeHint.longValue());
            if (preFetchPolicyResult.decision() == AcceptanceDecision.DENY) {
                return cleanupAndReturn(ProcessingResult.denied(ref, preFetchPolicyResult.reason()));
            }
        }

        // 1. Fetch raw content
        FetchedResource raw;
        try {
            raw = resourceProvider.fetch(ref);
        } catch (IOException e) {
            return ProcessingResult.failed(ref, "Failed to fetch resource: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ProcessingResult.failed(ref, "Invalid resource reference: " + e.getMessage());
        }

        if (raw == null) {
            return ProcessingResult.failed(ref, "Resource provider returned null content");
        }

        long sizeBytes = raw.sizeBytes();

        // 2. Policy check via tamias
        PolicyReason policyResult = policy.evaluate(ref, sizeBytes);
        if (policyResult.decision() == AcceptanceDecision.DENY) {
            return cleanupAndReturn(ProcessingResult.denied(ref, policyResult.reason()));
        }

        // 3. Compute digest (chalcotheca)
        byte[] bytes = raw.bytes();
        ResourceDigest digest = ContentHasher.digest(bytes);
        
        // 4. Detect and extract content
        InspectionResult inspection = contentInspector.inspect(ref, bytes, raw.filename());
        if (!inspection.isSuccess()) {
            return ProcessingResult.failed(ref, inspection.errorMessage());
        }

        // 5. Index via anagraphai — skip blocks with null/empty text
        List<String> textBlocks = inspection.textBlocks();
        LexicalDocument.Builder docBuilder = LexicalDocument.builder(ref)
                .title(raw.filename())
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
        
        // 6. Check archive for unchanged content
        if (!archive.hasChanged(ref, digest)) {
            return ProcessingResult.unchanged(ref);
        }

        try {
            lexicalIndex.index(docBuilder.build());
            lexicalIndex.commit();
        } catch (IOException e) {
            return ProcessingResult.failed(ref, "Indexing failed: " + e.getMessage());
        }

        // 7. Record snapshot in archive
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
}
