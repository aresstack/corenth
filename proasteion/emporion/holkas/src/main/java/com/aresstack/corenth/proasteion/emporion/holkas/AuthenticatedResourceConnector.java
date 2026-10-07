package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.adyton.AccessHandle;
import com.aresstack.corenth.astu.VirtualResourceRef;

import java.io.IOException;

/**
 * A connector that can work with an access handle prepared by the archive counter's access
 * station (#10 Slice 3) instead of authenticating on its own.
 *
 * <p>The handle comes from Adyton through {@link BrokeredAcquisitionAccess}; the connector only
 * uses it and never closes it. The handle exposes no secret material.
 *
 * @param <H> the protocol-specific handle type
 */
public interface AuthenticatedResourceConnector<H extends AccessHandle> extends ResourceConnector {

    /** Returns the handle type this connector accepts. */
    Class<H> handleType();

    /** Fetches a resource with a prepared handle. */
    RawResource fetch(VirtualResourceRef ref, H handle) throws IOException;

    /** Lists a container with a prepared handle. */
    ResourceListing list(VirtualResourceRef ref, H handle) throws IOException;
}
