package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.io.IOException;

/**
 * Inward-facing port for fetching raw resource content.
 *
 * <p>Abstracts the connector layer (e.g. holkas) so that the acropolis
 * coordinator does not compile against outer adapter modules.
 */
public interface RawResourceProvider {

    /**
     * Fetches the raw content of the resource.
     *
     * @param ref the resource reference
     * @return the fetched content with metadata
     * @throws IOException if the resource cannot be read
     */
    FetchedResource fetch(VirtualResourceRef ref) throws IOException;
}
