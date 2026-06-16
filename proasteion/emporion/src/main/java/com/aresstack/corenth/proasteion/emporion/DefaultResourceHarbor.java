package com.aresstack.corenth.proasteion.emporion;

import com.aresstack.corenth.proasteion.emporion.deigma.ContentDetector;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionResult;
import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResource;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResourceMetadata;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceConnectorRegistry;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceListing;

import java.io.IOException;
import java.util.Arrays;

/**
 * Default harbor coordinator: Holkas fetch/list plus Deigma detection/extraction.
 *
 * <p>No policy, cache, archive or indexing decisions live here.
 */
public final class DefaultResourceHarbor implements ResourceHarbor {

    private static final int DETECTION_PREFIX_BYTES = 4096;

    private final ResourceConnectorRegistry connectorRegistry;
    private final ContentDetector contentDetector;
    private final ExtractionRegistry extractionRegistry;

    public DefaultResourceHarbor(ResourceConnectorRegistry connectorRegistry,
                                 ContentDetector contentDetector,
                                 ExtractionRegistry extractionRegistry) {
        if (connectorRegistry == null) {
            throw new IllegalArgumentException("connectorRegistry must not be null");
        }
        if (contentDetector == null) {
            throw new IllegalArgumentException("contentDetector must not be null");
        }
        if (extractionRegistry == null) {
            throw new IllegalArgumentException("extractionRegistry must not be null");
        }
        this.connectorRegistry = connectorRegistry;
        this.contentDetector = contentDetector;
        this.extractionRegistry = extractionRegistry;
    }

    @Override
    public HarborResult<HarborInspection> inspect(HarborRequest request) {
        if (request == null) {
            return HarborResult.failure("request must not be null");
        }
        try {
            ResourceConnector connector = connectorRegistry.require(request.resourceRef().uri().scheme());
            RawResource rawResource = connector.fetch(request.resourceRef());
            DetectedContentType detectedType = detect(rawResource);
            ResourceExtractor extractor = extractionRegistry.findExtractor(detectedType);
            if (extractor == null) {
                ExtractionResult failure = ExtractionResult.failure(rawResource.ref(), detectedType,
                        "No extractor registered for detected content type: " + detectedType.mimeType());
                return HarborResult.success(new HarborInspection(rawResource, detectedType, failure));
            }
            ExtractionRequest extractionRequest = new ExtractionRequest(rawResource.ref(), rawResource.content().bytes(),
                    filename(rawResource), contentType(rawResource), detectedType);
            ExtractionResult extractionResult = extractor.extract(extractionRequest);
            return HarborResult.success(new HarborInspection(rawResource, detectedType, extractionResult));
        } catch (IOException e) {
            return HarborResult.failure(e.getMessage());
        } catch (RuntimeException e) {
            return HarborResult.failure(e.getMessage());
        }
    }

    @Override
    public HarborResult<ResourceListing> list(HarborRequest request) {
        if (request == null) {
            return HarborResult.failure("request must not be null");
        }
        try {
            ResourceConnector connector = connectorRegistry.require(request.resourceRef().uri().scheme());
            return HarborResult.success(connector.list(request.resourceRef()));
        } catch (IOException e) {
            return HarborResult.failure(e.getMessage());
        } catch (RuntimeException e) {
            return HarborResult.failure(e.getMessage());
        }
    }

    private DetectedContentType detect(RawResource rawResource) {
        byte[] bytes = rawResource.content().bytes();
        byte[] prefix = bytes.length <= DETECTION_PREFIX_BYTES
                ? bytes
                : Arrays.copyOf(bytes, DETECTION_PREFIX_BYTES);
        return contentDetector.detect(filename(rawResource), contentType(rawResource), prefix);
    }

    private String filename(RawResource rawResource) {
        RawResourceMetadata metadata = rawResource.metadata();
        return metadata != null ? metadata.name() : rawResource.filename();
    }

    private String contentType(RawResource rawResource) {
        RawResourceMetadata metadata = rawResource.metadata();
        return metadata != null ? metadata.contentType() : null;
    }
}
