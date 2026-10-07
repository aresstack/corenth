package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;

import java.io.IOException;

/**
 * Internal acquisition port for the bronze archive.
 *
 * <p>This port is used by Chalcotheca internally when Tamias allows acquisition
 * of a missing or stale resource. Holkas adapters implement this port.
 *
 * <p><strong>This is NOT a client-facing API.</strong> External callers (UI, bot,
 * plugin, service) must request resources through the mediated access service,
 * never through this port directly.
 */
public interface AcquisitionPort {

    /**
     * Fetches content for the given bookmark URI from the external source.
     *
     * @param uri the resource to acquire
     * @return the acquired bronze content
     * @throws IOException if acquisition fails
     */
    BronzeContent fetchContent(BookmarkUri uri) throws IOException;

    /**
     * Lists children of a container/directory resource at the given URI.
     *
     * @param uri the container resource to list
     * @return the bronze listing
     * @throws IOException if listing fails
     */
    BronzeListing listChildren(BookmarkUri uri) throws IOException;

    /**
     * Fetches content with a prepared authentication capability (#10 Slice 3).
     *
     * <p>Ports without authenticated sources keep the default: without a capability it
     * delegates to {@link #fetchContent(BookmarkUri)}, with one it refuses the acquisition.
     * The caller owns and closes the capability.
     *
     * @param uri the resource to acquire
     * @param capability the prepared capability, or {@code null} if none is required
     * @return the acquired bronze content
     * @throws IOException if acquisition fails or the capability is not supported
     */
    default BronzeContent fetchContent(BookmarkUri uri, AcquisitionCapability capability) throws IOException {
        if (capability != null) {
            throw new IOException("This acquisition port does not support authenticated acquisition");
        }
        return fetchContent(uri);
    }

    /**
     * Lists children with a prepared authentication capability (#10 Slice 3).
     *
     * @param uri the container resource to list
     * @param capability the prepared capability, or {@code null} if none is required
     * @return the bronze listing
     * @throws IOException if listing fails or the capability is not supported
     * @see #fetchContent(BookmarkUri, AcquisitionCapability)
     */
    default BronzeListing listChildren(BookmarkUri uri, AcquisitionCapability capability) throws IOException {
        if (capability != null) {
            throw new IOException("This acquisition port does not support authenticated acquisition");
        }
        return listChildren(uri);
    }
}
