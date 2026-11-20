package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.ContentInspector;
import com.aresstack.corenth.astu.acropolis.InspectionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentDetector;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adapter from the Deigma detector/extractor contracts to the Acropolis {@link ContentInspector} port.
 *
 * <p>It lives in the composition module because it is the only place that may know both the
 * lifecycle port and a concrete outer adapter family; Deigma itself stays free of lifecycle
 * coupling. The adapter only translates: detection and extraction are Deigma's, indexing and
 * policy decisions belong to the lifecycle. Metadata blocks are not passed on because
 * {@link InspectionResult} carries text-bearing blocks only.
 */
final class DeigmaContentInspector implements ContentInspector {

    /** Number of leading bytes handed to the detector for magic-byte detection. */
    private static final int DETECTION_PREFIX_BYTES = 4096;

    private final ContentDetector detector;
    private final ExtractionRegistry extractors;

    DeigmaContentInspector(ContentDetector detector, ExtractionRegistry extractors) {
        if (detector == null) throw new IllegalArgumentException("detector must not be null");
        if (extractors == null) throw new IllegalArgumentException("extractors must not be null");
        this.detector = detector;
        this.extractors = extractors;
    }

    @Override
    public InspectionResult inspect(VirtualResourceRef ref, byte[] content, String filenameHint) {
        if (ref == null || content == null) {
            return InspectionResult.failure("resource reference and content are required for inspection");
        }
        try {
            DetectedContentType detectedType = detector.detect(filenameHint, null, prefix(content));
            ResourceExtractor extractor = extractors.findExtractor(detectedType);
            if (extractor == null) {
                return InspectionResult.failure("No extractor for content type: " + detectedType.mimeType());
            }
            ExtractionResult extraction = extractor.extract(
                    new ExtractionRequest(ref, content, filenameHint, null, detectedType));
            if (!extraction.isSuccess()) {
                return InspectionResult.failure("Extraction failed: " + extraction.errorMessage());
            }
            return InspectionResult.success(detectedType.mimeType(), textBlocks(extraction.document().blocks()));
        } catch (RuntimeException e) {
            return InspectionResult.failure("Inspection failed: " + e.getMessage());
        }
    }

    private static List<String> textBlocks(List<ExtractedBlock> blocks) {
        List<String> texts = new ArrayList<String>();
        for (ExtractedBlock block : blocks) {
            if (block.kind() != BlockKind.METADATA) {
                texts.add(block.text());
            }
        }
        return texts;
    }

    private static byte[] prefix(byte[] content) {
        return content.length <= DETECTION_PREFIX_BYTES
                ? content
                : Arrays.copyOf(content, DETECTION_PREFIX_BYTES);
    }
}
