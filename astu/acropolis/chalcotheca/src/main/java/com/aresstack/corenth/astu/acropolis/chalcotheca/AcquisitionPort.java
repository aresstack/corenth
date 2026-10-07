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
     * @throws SourceAbsentException if the source confirms that the resource does not exist
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

    /**
     * Returns whether {@link #fetchMetadata(BookmarkUri, AcquisitionCapability)} can answer for
     * this resource (#10 Slice 5).
     *
     * <p>The counter asks before preparing access, so that a source without metadata never
     * triggers a credential request just to report that nothing is available. The default is
     * {@code false}.
     *
     * @param uri the resource to inspect
     * @return {@code true} if the port reads metadata for this resource
     */
    default boolean offersMetadata(BookmarkUri uri) {
        return false;
    }

    /**
     * Reads source metadata (size, name, modification time) without acquiring the payload
     * (#10 Slice 5).
     *
     * <p>The default reports that the source offers no metadata by returning {@code null}.
     *
     * @param uri the resource to inspect
     * @param capability the prepared capability, or {@code null} if none is required
     * @return the metadata, or {@code null} if the source offers no metadata
     * @throws SourceAbsentException if the source confirms that the resource does not exist
     * @throws IOException if reading the metadata fails
     */
    default BronzeMetadata fetchMetadata(BookmarkUri uri, AcquisitionCapability capability) throws IOException {
        return null;
    }
}
