package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.ResourceFingerprint;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceDigest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceSnapshot;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AcceptanceDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PolicyReason;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentDetector;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResource;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceConnector;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Orchestrates the walking skeleton pipeline for resource lifecycle processing.
 *
 * <p>Connects:
 * <ol>
 *   <li>{@code holkas} — fetch raw content</li>
 *   <li>{@code deigma} — detect and extract</li>
 *   <li>{@code tamias} — policy decision</li>
 *   <li>{@code chalcotheca} — snapshot/cache</li>
 *   <li>{@code anagraphai} — lexical indexing</li>
 * </ol>
 */
public final class ResourceLifecycleCoordinator {

    private final ResourceConnector connector;
    private final ContentDetector contentDetector;
    private final ExtractionRegistry extractionRegistry;
    private final ResourcePolicy policy;
    private final ResourceArchive archive;
    private final LexicalIndex lexicalIndex;

    public ResourceLifecycleCoordinator(ResourceConnector connector,
                                         ContentDetector contentDetector,
                                         ExtractionRegistry extractionRegistry,
                                         ResourcePolicy policy,
                                         ResourceArchive archive,
                                         LexicalIndex lexicalIndex) {
        if (connector == null) throw new IllegalArgumentException("connector must not be null");
        if (contentDetector == null) throw new IllegalArgumentException("contentDetector must not be null");
        if (extractionRegistry == null) throw new IllegalArgumentException("extractionRegistry must not be null");
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        if (archive == null) throw new IllegalArgumentException("archive must not be null");
        if (lexicalIndex == null) throw new IllegalArgumentException("lexicalIndex must not be null");
        this.connector = connector;
        this.contentDetector = contentDetector;
        this.extractionRegistry = extractionRegistry;
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
        // 1. Fetch raw content via holkas
        RawResource raw;
        try {
            raw = connector.fetch(ref);
        } catch (IOException e) {
            return ProcessingResult.failed(ref, "Failed to fetch resource: " + e.getMessage());
        }

        long sizeBytes = raw.content().sizeBytes();

        // 2. Policy check via tamias
        PolicyReason policyResult = policy.evaluate(ref, sizeBytes);
        if (policyResult.decision() == AcceptanceDecision.DENY) {
            return ProcessingResult.denied(ref, policyResult.reason());
        }

        // 3. Compute digest and check archive (chalcotheca)
        byte[] bytes = raw.content().bytes();
        ResourceDigest digest = computeDigest(bytes);
        if (!archive.hasChanged(ref, digest)) {
            return ProcessingResult.unchanged(ref);
        }

        // 4. Detect and extract content via deigma
        DetectedContentType detectedType = contentDetector.detect(
                raw.filename(), null, bytes.length > 64 ? copyPrefix(bytes, 64) : bytes);

        ResourceExtractor extractor = extractionRegistry.findExtractor(detectedType);
        if (extractor == null) {
            return ProcessingResult.failed(ref, "No extractor for content type: " + detectedType.mimeType());
        }

        ExtractionRequest request = new ExtractionRequest(ref, bytes, raw.filename(), null, detectedType);
        ExtractionResult extraction = extractor.extract(request);
        if (!extraction.isSuccess()) {
            return ProcessingResult.failed(ref, "Extraction failed: " + extraction.errorMessage());
        }

        // 5. Index via anagraphai
        ExtractedDocument doc = extraction.document();
        LexicalDocument.Builder docBuilder = LexicalDocument.builder(ref)
                .title(raw.filename())
                .contentType(detectedType.mimeType());

        List<ExtractedBlock> blocks = doc.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            docBuilder.addChunk(new LexicalChunk(i, blocks.get(i).text()));
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

    private static byte[] copyPrefix(byte[] src, int length) {
        byte[] prefix = new byte[length];
        System.arraycopy(src, 0, prefix, 0, length);
        return prefix;
    }
}
