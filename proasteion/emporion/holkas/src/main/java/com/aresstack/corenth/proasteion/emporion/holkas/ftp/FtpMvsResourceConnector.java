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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * FTP/MVS raw-resource connector skeleton backed by Adyton access handles.
 */
public final class FtpMvsResourceConnector implements ResourceConnector {

    private final AccessBroker accessBroker;
    private final AccessRequest accessRequest;
    private final AuthenticationStrategy<FtpAccessHandle> authenticationStrategy;
    private final FtpMvsBookmarkMapper bookmarkMapper;
    private final MvsListingMapper listingMapper;

    public FtpMvsResourceConnector(AccessBroker accessBroker,
                                   AccessRequest accessRequest,
                                   AuthenticationStrategy<FtpAccessHandle> authenticationStrategy) {
        if (accessBroker == null) {
            throw new IllegalArgumentException("accessBroker must not be null");
        }
        if (accessRequest == null) {
            throw new IllegalArgumentException("accessRequest must not be null");
        }
        if (authenticationStrategy == null) {
            throw new IllegalArgumentException("authenticationStrategy must not be null");
        }
        this.accessBroker = accessBroker;
        this.accessRequest = accessRequest;
        this.authenticationStrategy = authenticationStrategy;
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
        try {
            return accessBroker.withAccess(accessRequest, authenticationStrategy,
                    new AccessOperation<FtpAccessHandle, RawResource>() {
                        @Override
                        public RawResource execute(FtpAccessHandle handle) throws AccessException {
                            try {
                                MvsLocation location = bookmarkMapper.locationOf(ref.uri());
                                byte[] bytes = handle.session().readBytes(location, ResourceReadMode.DEFAULT);
                                RawResourceContent content = new RawResourceContent(bytes);
                                RawResourceMetadata metadata = new RawResourceMetadata(
                                        location.displayName(), null, content.sizeBytes(), 0L,
                                        System.currentTimeMillis(), VirtualResourceKind.FILE);
                                return new RawResource(ref, content, metadata);
                            } catch (IOException e) {
                                throw new AccessException("FTP/MVS fetch failed", e);
                            }
                        }
                    });
        } catch (AuthCancelledException e) {
            throw new ResourceConnectorException("FTP/MVS authentication cancelled", e);
        } catch (AccessException e) {
            throw new ResourceConnectorException(e.getMessage(), e);
        }
    }

    @Override
    public ResourceListing list(final VirtualResourceRef ref) throws IOException {
        validateRef(ref);
        try {
            return accessBroker.withAccess(accessRequest, authenticationStrategy,
                    new AccessOperation<FtpAccessHandle, ResourceListing>() {
                        @Override
                        public ResourceListing execute(FtpAccessHandle handle) throws AccessException {
                            try {
                                MvsLocation parent = bookmarkMapper.locationOf(ref.uri());
                                List<String> names = handle.session().listNames(parent);
                                List<MvsListingEntry> mapped = listingMapper.mapNames(parent, names);
                                List<ResourceListingEntry> entries = new ArrayList<ResourceListingEntry>();
                                for (MvsListingEntry entry : mapped) {
                                    BookmarkUri childUri = bookmarkMapper.childUri(ref.uri(), entry.location());
                                    VirtualResourceRef childRef = new VirtualResourceRef(childUri, entry.kind());
                                    entries.add(new ResourceListingEntry(childRef, entry.name(), entry.kind(), null));
                                }
                                return new ResourceListing(ref, entries, System.currentTimeMillis());
                            } catch (IOException e) {
                                throw new AccessException("FTP/MVS list failed", e);
                            }
                        }
                    });
        } catch (AuthCancelledException e) {
            throw new ResourceConnectorException("FTP/MVS authentication cancelled", e);
        } catch (AccessException e) {
            throw new ResourceConnectorException(e.getMessage(), e);
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
}
