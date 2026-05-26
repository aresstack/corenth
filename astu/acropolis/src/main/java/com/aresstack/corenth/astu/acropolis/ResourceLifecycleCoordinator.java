package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.ResourceFingerprint;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceDigest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceSnapshot;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AcceptanceDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PolicyReason;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    public ResourceLifecycleCoordinator(RawResourceProvider resourceProvider,
                                         ContentInspector contentInspector,
                                         ResourcePolicy policy,
                                         ResourceArchive archive,
                                         LexicalIndex lexicalIndex) {
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
    }

    /**
     * Processes a single resource through the full pipeline.
     *
     * @param ref the resource reference to process
     * @return the processing result
     */
    public ProcessingResult process(VirtualResourceRef ref) {
        // 1. Fetch raw content
        FetchedResource raw;
        try {
            raw = resourceProvider.fetch(ref);
        } catch (IOException e) {
            return ProcessingResult.failed(ref, "Failed to fetch resource: " + e.getMessage());
        }

        long sizeBytes = raw.sizeBytes();

        // 2. Policy check via tamias
        PolicyReason policyResult = policy.evaluate(ref, sizeBytes);
        if (policyResult.decision() == AcceptanceDecision.DENY) {
            return ProcessingResult.denied(ref, policyResult.reason());
        }

        // 3. Compute digest and check archive (chalcotheca)
        byte[] bytes = raw.bytes();
        ResourceDigest digest = computeDigest(bytes);
        if (!archive.hasChanged(ref, digest)) {
            return ProcessingResult.unchanged(ref);
        }

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
                docBuilder.addChunk(new LexicalChunk(chunkIndex, text));
                chunkIndex++;
            }
        }

        if (chunkIndex == 0) {
            // Extraction succeeded but no text-bearing blocks remain
            return ProcessingResult.failed(ref, "No indexable text content after extraction");
        }

        try {
            lexicalIndex.index(docBuilder.build());
            lexicalIndex.commit();
        } catch (IOException e) {
            return ProcessingResult.failed(ref, "Indexing failed: " + e.getMessage());
        }

        // 6. Record snapshot in archive
        archive.store(new ResourceSnapshot(ref, digest, System.currentTimeMillis()));

        return ProcessingResult.indexed(ref);
    }

    private static ResourceDigest computeDigest(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return new ResourceDigest(new ResourceFingerprint("SHA-256", hex.toString()), content.length);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
