package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceRef;

import java.io.IOException;

/**
 * Port for fetching raw resources by their virtual reference.
 *
 * <p>Implementations connect to a specific protocol/scheme and return
 * raw bytes plus basic metadata.
 */
public interface ResourceConnector {

    /**
     * Returns the scheme this connector handles.
     */
    ResourceScheme supportedScheme();

    /**
     * Fetches the raw resource content addressed by the given reference.
     *
     * @param ref the resource reference (must use a scheme supported by this connector)
     * @return the raw resource with content and metadata
     * @throws IOException if the resource cannot be read
     * @throws IllegalArgumentException if the scheme is not supported
     */
    RawResource fetch(VirtualResourceRef ref) throws IOException;
}
