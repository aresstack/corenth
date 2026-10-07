package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.adyton.AccessHandle;
import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionCapability;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionLimitExceededException;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionPort;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeMetadata;
import com.aresstack.corenth.astu.acropolis.chalcotheca.ContentHasher;
import com.aresstack.corenth.astu.acropolis.chalcotheca.SourceAbsentException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chalcotheca acquisition adapter backed by Holkas connectors.
 *
 * <p>This class keeps Holkas behind the mediated bronze-access boundary. Clients
 * still use Chalcotheca; Chalcotheca calls this internal acquisition port.
 */
public final class HolkasAcquisitionPort implements AcquisitionPort {

    private final ResourceConnectorRegistry connectorRegistry;

    public HolkasAcquisitionPort(ResourceConnectorRegistry connectorRegistry) {
        if (connectorRegistry == null) {
            throw new IllegalArgumentException("connectorRegistry must not be null");
        }
        this.connectorRegistry = connectorRegistry;
    }

    @Override
    public BronzeContent fetchContent(BookmarkUri uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
        ResourceConnector connector = connectorRegistry.require(uri.scheme());
        RawResource raw;
        try {
            raw = connector.fetch(ref);
        } catch (ResourceNotFoundException e) {
            throw absent(e);
        }
        byte[] bytes = raw.content().bytes();
        return new BronzeContent(uri, bytes, ContentHasher.digest(bytes), System.currentTimeMillis());
    }

    @Override
    public BronzeListing listChildren(BookmarkUri uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.DIRECTORY);
        ResourceConnector connector = connectorRegistry.require(uri.scheme());
        return toBronze(uri, connector.list(ref));
    }

    /**
     * Fetches content with a capability prepared by {@link BrokeredAcquisitionAccess}.
     *
     * <p>The capability's handle is passed to an {@link AuthenticatedResourceConnector} of the
     * matching handle type. The caller keeps ownership of the capability and closes it.
     */
    @Override
    public BronzeContent fetchContent(BookmarkUri uri, AcquisitionCapability capability) throws IOException {
        if (capability == null) {
            return fetchContent(uri);
        }
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
        RawResource raw;
        try {
            raw = authenticated(uri).fetchWith(ref, capability);
        } catch (ResourceNotFoundException e) {
            throw absent(e);
        }
        byte[] bytes = raw.content().bytes();
        return new BronzeContent(uri, bytes, ContentHasher.digest(bytes), System.currentTimeMillis());
    }

    /**
     * Fetches content and reads at most {@code maxBytes} bytes through the connector (#10 Slice 5).
     *
     * <p>Authenticated connectors cannot bound their read yet; with a capability the acquisition
     * is refused instead of read unbounded.
     */
    @Override
    public BronzeContent fetchContent(BookmarkUri uri, AcquisitionCapability capability, long maxBytes)
            throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        if (capability != null) {
            throw new ResourceConnectorException("Authenticated connectors cannot bound an acquisition yet: " + uri);
        }
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
        RawResource raw;
        try {
            raw = connectorRegistry.require(uri.scheme()).fetch(ref, maxBytes);
        } catch (ResourceNotFoundException e) {
            throw absent(e);
        } catch (ResourceSizeLimitExceededException e) {
            throw new AcquisitionLimitExceededException(e.observedBytes(), e.getMessage());
        }
        byte[] bytes = raw.content().bytes();
        return new BronzeContent(uri, bytes, ContentHasher.digest(bytes), System.currentTimeMillis());
    }

    /**
     * Lists children with a capability prepared by {@link BrokeredAcquisitionAccess}.
     *
     * @see #fetchContent(BookmarkUri, AcquisitionCapability)
     */
    @Override
    public BronzeListing listChildren(BookmarkUri uri, AcquisitionCapability capability) throws IOException {
        if (capability == null) {
            return listChildren(uri);
        }
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.DIRECTORY);
        return toBronze(uri, authenticated(uri).listWith(ref, capability));
    }

    /**
     * Offers metadata for registered connectors that read without prepared access. Authenticated
     * connectors offer none yet, so the counter never prepares access for a metadata read.
     */
    @Override
    public boolean offersMetadata(BookmarkUri uri) {
        if (uri == null) {
            return false;
        }
        ResourceConnector connector = connectorRegistry.find(uri.scheme());
        return connector != null && !(connector instanceof AuthenticatedResourceConnector);
    }

    /**
     * Reads source metadata through the connector without fetching the payload (#10 Slice 5).
     * Authenticated connectors offer no metadata yet; the method then returns {@code null}.
     */
    @Override
    public BronzeMetadata fetchMetadata(BookmarkUri uri, AcquisitionCapability capability) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        if (capability != null) {
            return null;
        }
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
        RawResourceMetadata metadata;
        try {
            metadata = connectorRegistry.require(uri.scheme()).metadata(ref);
        } catch (ResourceNotFoundException e) {
            throw absent(e);
        }
        if (metadata == null) {
            return null;
        }
        return new BronzeMetadata(uri, metadata.name(), metadata.contentType(), metadata.sizeBytes(),
                metadata.modifiedAtMillis(), metadata.observedAtMillis());
    }

    private static SourceAbsentException absent(ResourceNotFoundException e) {
        return new SourceAbsentException(e.getMessage(), e);
    }

    private AuthenticatedCall authenticated(BookmarkUri uri) throws IOException {
        ResourceConnector connector = connectorRegistry.require(uri.scheme());
        if (!(connector instanceof AuthenticatedResourceConnector)) {
            throw new ResourceConnectorException("Connector for " + uri.scheme() + " does not accept prepared access");
        }
        return new AuthenticatedCall((AuthenticatedResourceConnector<?>) connector);
    }

    private static BronzeListing toBronze(BookmarkUri uri, ResourceListing listing) {
        List<BronzeListing.Entry> entries = new ArrayList<BronzeListing.Entry>();
        for (ResourceListingEntry entry : listing.entries()) {
            entries.add(new BronzeListing.Entry(entry.ref().uri(), entry.name(), entry.kind()));
        }
        return new BronzeListing(uri, entries, listing.observedAtMillis());
    }

    /** Unwraps a Holkas capability and checks its handle type against the connector. */
    private static final class AuthenticatedCall {
        private final AuthenticatedResourceConnector<?> connector;

        AuthenticatedCall(AuthenticatedResourceConnector<?> connector) {
            this.connector = connector;
        }

        RawResource fetchWith(VirtualResourceRef ref, AcquisitionCapability capability) throws IOException {
            return fetch(connector, ref, capability);
        }

        ResourceListing listWith(VirtualResourceRef ref, AcquisitionCapability capability) throws IOException {
            return list(connector, ref, capability);
        }

        private static <H extends AccessHandle> RawResource fetch(AuthenticatedResourceConnector<H> connector,
                                                                 VirtualResourceRef ref,
                                                                 AcquisitionCapability capability) throws IOException {
            return connector.fetch(ref, handleOf(connector, capability));
        }

        private static <H extends AccessHandle> ResourceListing list(AuthenticatedResourceConnector<H> connector,
                                                                    VirtualResourceRef ref,
                                                                    AcquisitionCapability capability) throws IOException {
            return connector.list(ref, handleOf(connector, capability));
        }

        private static <H extends AccessHandle> H handleOf(AuthenticatedResourceConnector<H> connector,
                                                          AcquisitionCapability capability) throws IOException {
            if (!(capability instanceof HandleCapability)) {
                throw new ResourceConnectorException("Capability was not prepared by the Holkas access station");
            }
            HandleCapability prepared = (HandleCapability) capability;
            if (prepared.isClosed()) {
                throw new ResourceConnectorException("Capability is already closed");
            }
            AccessHandle handle = prepared.handle();
            if (!connector.handleType().isInstance(handle)) {
                throw new ResourceConnectorException("Prepared handle does not match the connector's handle type");
            }
            return connector.handleType().cast(handle);
        }
    }
}
