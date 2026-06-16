package com.aresstack.corenth.proasteion.emporion;

import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResource;

/**
 * Harbor-level result of fetch + shallow detection + extraction.
 */
public final class HarborInspection {

    private final RawResource rawResource;
    private final DetectedContentType detectedContentType;
    private final ExtractionResult extractionResult;

    public HarborInspection(RawResource rawResource, DetectedContentType detectedContentType,
                            ExtractionResult extractionResult) {
        if (rawResource == null) {
            throw new IllegalArgumentException("rawResource must not be null");
        }
        if (detectedContentType == null) {
            throw new IllegalArgumentException("detectedContentType must not be null");
        }
        if (extractionResult == null) {
            throw new IllegalArgumentException("extractionResult must not be null");
        }
        this.rawResource = rawResource;
        this.detectedContentType = detectedContentType;
        this.extractionResult = extractionResult;
    }

    public RawResource rawResource() {
        return rawResource;
    }

    public DetectedContentType detectedContentType() {
        return detectedContentType;
    }

    public ExtractionResult extractionResult() {
        return extractionResult;
    }
}
