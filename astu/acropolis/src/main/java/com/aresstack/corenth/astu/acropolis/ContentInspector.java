package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * Inward-facing port for content detection and extraction.
 *
 * <p>Abstracts the deigma detection/extraction layer so that the acropolis
 * coordinator does not compile against outer adapter modules.
 */
public interface ContentInspector {

    /**
     * Detects and extracts text content from the given raw bytes.
     *
     * @param ref          the resource reference
     * @param content      the raw content bytes
     * @param filenameHint optional filename hint for content detection
     * @return the inspection result
     */
    InspectionResult inspect(VirtualResourceRef ref, byte[] content, String filenameHint);
}
