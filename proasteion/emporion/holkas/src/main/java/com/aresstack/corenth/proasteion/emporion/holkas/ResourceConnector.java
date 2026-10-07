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
     * Returns {@code true} if this connector handles the given scheme.
     */
    default boolean supports(ResourceScheme scheme) {
        return supportedScheme().equals(scheme);
    }

    /**
     * Fetches the raw resource content addressed by the given reference.
     *
     * @param ref the resource reference (must use a scheme supported by this connector)
     * @return the raw resource with content and metadata
     * @throws IOException if the resource cannot be read
     * @throws IllegalArgumentException if the scheme is not supported
     */
    RawResource fetch(VirtualResourceRef ref) throws IOException;

    /**
     * Fetches the raw resource and reads at most {@code maxBytes} bytes from the source
     * (#10 Slice 5).
     *
     * <p>A source with more bytes ends the read with a {@link ResourceSizeLimitExceededException}
     * after at most {@code maxBytes + 1} bytes. The default refuses: a connector that cannot bound
     * its read must not read a resource of unknown size completely.
     *
     * @param ref      the resource reference
     * @param maxBytes the largest admitted payload in bytes; {@code >= 0}
     * @return the raw resource of at most {@code maxBytes} bytes
     * @throws ResourceSizeLimitExceededException if the source holds more than {@code maxBytes} bytes
     * @throws IOException if the resource cannot be read or the connector cannot bound the read
     */
    default RawResource fetch(VirtualResourceRef ref, long maxBytes) throws IOException {
        throw new ResourceConnectorException("Connector for " + supportedScheme()
                + " cannot bound a fetch to " + maxBytes + " bytes");
    }

    /**
     * Lists child resources addressed by the given container reference.
     *
     * @param ref the container resource reference
     * @return the raw resource listing
     * @throws IOException if the resource cannot be listed
     */
    ResourceListing list(VirtualResourceRef ref) throws IOException;

    /**
     * Reads the metadata of a resource without fetching its payload (#10 Slice 5).
     *
     * <p>The default reports that the connector offers no metadata by returning {@code null}.
     *
     * @throws ResourceNotFoundException if the source confirms that the resource does not exist
     * @throws IOException if reading the metadata fails
     */
    default RawResourceMetadata metadata(VirtualResourceRef ref) throws IOException {
        return null;
    }
}
