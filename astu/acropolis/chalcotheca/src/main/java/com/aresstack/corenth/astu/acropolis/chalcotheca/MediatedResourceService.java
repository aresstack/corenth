package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessDecisionType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessReasonCode;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mediated bronze archive access service.
 *
 * <p>This is the archive counter: all external access to bronze resources flows
 * through this service. It consults Tamias for every operation, uses the
 * internal {@link AcquisitionPort} for acquisition when allowed, and manages
 * the cached bronze state.
 *
 * <p><strong>Callers must not talk to connectors directly.</strong> They request
 * resources from this service by {@link BookmarkUri}. The service decides, via
 * Tamias, whether the caller may see, list, fetch, refresh, index, or delete
 * that resource.
 */
public final class MediatedResourceService {

    private final ResourceAccessPolicy accessPolicy;
    private final AcquisitionPort acquisitionPort;
    private final ResourceArchive archive;

    // In-memory bronze state stores (simple for the walking skeleton)
    private final Map<BookmarkUri, BronzeListing> listingCache = new ConcurrentHashMap<BookmarkUri, BronzeListing>();
    private final Map<BookmarkUri, BronzeContent> contentCache = new ConcurrentHashMap<BookmarkUri, BronzeContent>();
    private final Map<BookmarkUri, BronzeMetadata> metadataCache = new ConcurrentHashMap<BookmarkUri, BronzeMetadata>();

    public MediatedResourceService(ResourceAccessPolicy accessPolicy,
                                   AcquisitionPort acquisitionPort,
                                   ResourceArchive archive) {
        if (accessPolicy == null) throw new IllegalArgumentException("accessPolicy must not be null");
        if (acquisitionPort == null) throw new IllegalArgumentException("acquisitionPort must not be null");
        if (archive == null) throw new IllegalArgumentException("archive must not be null");
        this.accessPolicy = accessPolicy;
        this.acquisitionPort = acquisitionPort;
        this.archive = archive;
    }

    /**
     * Lists children of a container resource, mediated by Tamias.
     *
     * @param request the access request (operation should be LIST_CHILDREN)
     * @return the result of the mediated listing operation
     */
    public MediatedResult<BronzeListing> listChildren(ResourceAccessRequest request) {
        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();

        // Check if we have a cached listing
        BronzeListing cached = listingCache.get(uri);
        if (cached != null && decision.type() == AccessDecisionType.ALLOW_CACHED_ONLY) {
            return MediatedResult.success(cached, decision);
        }

        // If allowed, acquire if missing or stale
        if (cached == null && decision.type() == AccessDecisionType.ALLOW) {
            try {
                BronzeListing acquired = acquisitionPort.listChildren(uri);
                listingCache.put(uri, acquired);
                return MediatedResult.success(acquired, decision);
            } catch (IOException e) {
                return MediatedResult.error("Acquisition failed: " + e.getMessage());
            }
        }

        if (cached != null) {
            return MediatedResult.success(cached, decision);
        }

        return MediatedResult.error("No cached listing available and acquisition not permitted");
    }

    /**
     * Reads content of a resource, mediated by Tamias.
     *
     * @param request the access request (operation should be READ_CONTENT)
     * @return the result of the mediated content read
     */
    public MediatedResult<BronzeContent> readContent(ResourceAccessRequest request) {
        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();

        // Check if we have cached content
        BronzeContent cached = contentCache.get(uri);
        if (cached != null && decision.type() == AccessDecisionType.ALLOW_CACHED_ONLY) {
            return MediatedResult.success(cached, decision);
        }

        // If allowed, acquire if missing
        if (cached == null && decision.type() == AccessDecisionType.ALLOW) {
            try {
                BronzeContent acquired = acquisitionPort.fetchContent(uri);
                contentCache.put(uri, acquired);
                // Also update the archive snapshot
                VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
                archive.store(new ResourceSnapshot(ref, acquired.digest(), acquired.fetchedAtMillis()));
                return MediatedResult.success(acquired, decision);
            } catch (IOException e) {
                return MediatedResult.error("Acquisition failed: " + e.getMessage());
            }
        }

        if (cached != null) {
            return MediatedResult.success(cached, decision);
        }

        return MediatedResult.error("No cached content available and acquisition not permitted");
    }

    /**
     * Deletes a bronze archive entry (tombstones it).
     *
     * <p>This removes the cached state and the archive snapshot.
     * User-specific denial does NOT call this method — only explicit
     * blacklist/tombstone policy does.
     *
     * @param request the access request (operation should be DELETE_ARCHIVE_ENTRY)
     * @return the result
     */
    public MediatedResult<Void> deleteEntry(ResourceAccessRequest request) {
        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();
        listingCache.remove(uri);
        contentCache.remove(uri);
        metadataCache.remove(uri);

        // Remove from archive
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
        archive.remove(ref);

        return MediatedResult.success(null, decision);
    }

    /**
     * Returns whether the archive has cached state for the given URI.
     * This is useful for testing that denial does NOT delete global state.
     */
    public boolean hasCachedContent(BookmarkUri uri) {
        return contentCache.containsKey(uri);
    }

    /**
     * Returns whether the archive has a cached listing for the given URI.
     */
    public boolean hasCachedListing(BookmarkUri uri) {
        return listingCache.containsKey(uri);
    }

    /**
     * Stores content in the cache directly (for pre-populating in tests or migration).
     */
    public void storeBronzeContent(BronzeContent content) {
        contentCache.put(content.uri(), content);
    }

    /**
     * Stores a listing in the cache directly.
     */
    public void storeBronzeListing(BronzeListing listing) {
        listingCache.put(listing.containerUri(), listing);
    }
}
