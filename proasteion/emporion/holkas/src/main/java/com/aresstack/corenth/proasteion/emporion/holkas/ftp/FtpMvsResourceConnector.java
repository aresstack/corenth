package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import com.aresstack.corenth.adyton.AccessBroker;
import com.aresstack.corenth.adyton.AccessException;
import com.aresstack.corenth.adyton.AccessOperation;
import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthenticationStrategy;
import com.aresstack.corenth.adyton.AuthCancelledException;
import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResource;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResourceContent;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResourceMetadata;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceConnectorException;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceListing;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceListingEntry;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceReadMode;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsListingEntry;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsListingMapper;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsLocation;
import com.aresstack.corenth.proasteion.platform.network.NetworkAccessPolicy;
import com.aresstack.corenth.proasteion.platform.network.NetworkAccessRequest;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlan;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlanner;
import com.aresstack.corenth.proasteion.platform.network.NetworkRouteStage;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MVS resource connector backed by broker-managed access handles.
 */
public final class FtpMvsResourceConnector implements ResourceConnector {

    private final AccessBroker accessBroker;
    private final AccessRequest accessRequest;
    private final AuthenticationStrategy<FtpAccessHandle> authenticationStrategy;
    private final NetworkRoutePlanner routePlanner;
    private final NetworkAccessPolicy networkPolicy;
    private final FtpMvsBookmarkMapper bookmarkMapper;
    private final MvsListingMapper listingMapper;

    public FtpMvsResourceConnector(AccessBroker accessBroker,
                                   AccessRequest accessRequest,
                                   AuthenticationStrategy<FtpAccessHandle> authenticationStrategy) {
        this(accessBroker, accessRequest, authenticationStrategy,
                new DirectNetworkRoutePlanner(), NetworkAccessPolicy.direct());
    }

    public FtpMvsResourceConnector(AccessBroker accessBroker,
                                   AccessRequest accessRequest,
                                   AuthenticationStrategy<FtpAccessHandle> authenticationStrategy,
                                   NetworkRoutePlanner routePlanner,
                                   NetworkAccessPolicy networkPolicy) {
        if (accessBroker == null) {
            throw new IllegalArgumentException("accessBroker must not be null");
        }
        if (accessRequest == null) {
            throw new IllegalArgumentException("accessRequest must not be null");
        }
        if (authenticationStrategy == null) {
            throw new IllegalArgumentException("authenticationStrategy must not be null");
        }
        if (routePlanner == null) {
            throw new IllegalArgumentException("routePlanner must not be null");
        }
        if (networkPolicy == null) {
            throw new IllegalArgumentException("networkPolicy must not be null");
        }
        this.accessBroker = accessBroker;
        this.accessRequest = accessRequest;
        this.authenticationStrategy = authenticationStrategy;
        this.routePlanner = routePlanner;
        this.networkPolicy = networkPolicy;
        this.bookmarkMapper = new FtpMvsBookmarkMapper();
        this.listingMapper = new MvsListingMapper();
    }

    @Override
    public ResourceScheme supportedScheme() {
        return ResourceScheme.FTP;
    }

    @Override
    public RawResource fetch(final VirtualResourceRef ref) throws IOException {
        validateRef(ref);
        final NetworkRoutePlan routePlan = planRoute(ref, "fetch");
        try {
            return accessBroker.withAccess(accessRequest, authenticationStrategy,
                    new AccessOperation<FtpAccessHandle, RawResource>() {
                        @Override
                        public RawResource execute(FtpAccessHandle handle) throws AccessException {
                            try {
                                MvsLocation location = bookmarkMapper.locationOf(ref.uri());
                                byte[] bytes = handle.session().readBytes(location, ResourceReadMode.DEFAULT, routePlan);
                                RawResourceContent content = new RawResourceContent(bytes);
                                RawResourceMetadata metadata = new RawResourceMetadata(
                                        location.displayName(), null, content.sizeBytes(), 0L,
                                        System.currentTimeMillis(), VirtualResourceKind.FILE);
                                return new RawResource(ref, content, metadata);
                            } catch (IOException e) {
                                throw new AccessException("MVS fetch failed", e);
                            }
                        }
                    });
        } catch (AuthCancelledException e) {
            throw new ResourceConnectorException("MVS access cancelled", e);
        } catch (AccessException e) {
            throw new ResourceConnectorException(e.getMessage(), e);
        }
    }

    @Override
    public ResourceListing list(final VirtualResourceRef ref) throws IOException {
        validateRef(ref);
        final NetworkRoutePlan routePlan = planRoute(ref, "list");
        try {
            return accessBroker.withAccess(accessRequest, authenticationStrategy,
                    new AccessOperation<FtpAccessHandle, ResourceListing>() {
                        @Override
                        public ResourceListing execute(FtpAccessHandle handle) throws AccessException {
                            try {
                                MvsLocation parent = bookmarkMapper.locationOf(ref.uri());
                                List<String> names = handle.session().listNames(parent, routePlan);
                                List<MvsListingEntry> mapped = listingMapper.mapNames(parent, names);
                                List<ResourceListingEntry> entries = new ArrayList<ResourceListingEntry>();
                                for (MvsListingEntry entry : mapped) {
                                    BookmarkUri childUri = bookmarkMapper.childUri(ref.uri(), entry.location());
                                    VirtualResourceRef childRef = new VirtualResourceRef(childUri, entry.kind());
                                    entries.add(new ResourceListingEntry(childRef, entry.name(), entry.kind(), null));
                                }
                                return new ResourceListing(ref, entries, System.currentTimeMillis());
                            } catch (IOException e) {
                                throw new AccessException("MVS list failed", e);
                            }
                        }
                    });
        } catch (AuthCancelledException e) {
            throw new ResourceConnectorException("MVS access cancelled", e);
        } catch (AccessException e) {
            throw new ResourceConnectorException(e.getMessage(), e);
        }
    }

    private NetworkRoutePlan planRoute(VirtualResourceRef ref, String operation) throws ResourceConnectorException {
        if (ref.uri().toURI() == null) {
            throw new ResourceConnectorException("MVS route planning requires a standard URI");
        }
        try {
            return routePlanner.plan(new NetworkAccessRequest(ref.uri().toURI(), "mvs-" + operation, networkPolicy));
        } catch (NetworkRoutingException e) {
            throw new ResourceConnectorException("MVS route planning failed", e);
        }
    }

    private void validateRef(VirtualResourceRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (!supports(ref.uri().scheme())) {
            throw new IllegalArgumentException("FtpMvsResourceConnector only supports ftp: scheme");
        }
    }

    private static final class DirectNetworkRoutePlanner implements NetworkRoutePlanner {
        @Override
        public NetworkRoutePlan plan(NetworkAccessRequest request) {
            return new NetworkRoutePlan(request.targetUri(),
                    Collections.singletonList(NetworkRouteStage.direct("mvs-default-direct")));
        }
    }
}
