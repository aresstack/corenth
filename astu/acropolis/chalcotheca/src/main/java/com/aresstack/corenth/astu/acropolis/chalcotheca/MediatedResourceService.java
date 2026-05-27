package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessDecisionType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessReasonCode;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;

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
 *
 * <p>Acquisition is a separate controlled decision: when a cache miss occurs,
 * the service issues a second {@link ResourceOperation#FETCH_EXTERNAL} request
 * to Tamias before invoking the internal {@link AcquisitionPort}.
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
     * <p>The request must carry {@link ResourceOperation#LIST_CHILDREN}. If the
     * cached listing is missing and external acquisition is needed, a second
     * policy evaluation with {@link ResourceOperation#FETCH_EXTERNAL} is performed.
     *
     * @param request the access request (operation must be LIST_CHILDREN)
     * @return the result of the mediated listing operation
     */
    public MediatedResult<BronzeListing> listChildren(ResourceAccessRequest request) {
        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.LIST_CHILDREN) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.UNKNOWN_RESOURCE,
                    "Operation mismatch: listChildren requires LIST_CHILDREN, got " + request.operation()));
        }

        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();
        BronzeListing cached = listingCache.get(uri);

        // ALLOW_CACHED_ONLY: return cached state only, never fetch externally
        if (decision.type() == AccessDecisionType.ALLOW_CACHED_ONLY) {
            if (cached != null) {
                return MediatedResult.success(cached, decision);
            }
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.CACHE_ONLY_ALLOWED,
                    "No cached listing available and external fetch not permitted"));
        }

        // ALLOW: return cached if present
        if (cached != null) {
            return MediatedResult.success(cached, decision);
        }

        // Cache miss: check if external acquisition is allowed (Fix 2)
        ResourceAccessRequest fetchRequest = new ResourceAccessRequest(
                request.actor(), uri, ResourceOperation.FETCH_EXTERNAL, request.purpose());
        ResourceAccessDecision fetchDecision = accessPolicy.evaluate(fetchRequest);
        if (!fetchDecision.isAllowed()) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    fetchDecision.reasonCode(),
                    "External acquisition denied: " + fetchDecision.explanation()));
        }

        // Acquire internally
        try {
            BronzeListing acquired = acquisitionPort.listChildren(uri);
            listingCache.put(uri, acquired);
            return MediatedResult.success(acquired, decision);
        } catch (IOException e) {
            return MediatedResult.error("Acquisition failed: " + e.getMessage());
        }
    }

    /**
     * Reads content of a resource, mediated by Tamias.
     *
     * <p>The request must carry {@link ResourceOperation#READ_CONTENT}. If the
     * cached content is missing and external acquisition is needed, a second
     * policy evaluation with {@link ResourceOperation#FETCH_EXTERNAL} is performed.
     *
     * @param request the access request (operation must be READ_CONTENT)
     * @return the result of the mediated content read
     */
    public MediatedResult<BronzeContent> readContent(ResourceAccessRequest request) {
        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.READ_CONTENT) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.UNKNOWN_RESOURCE,
                    "Operation mismatch: readContent requires READ_CONTENT, got " + request.operation()));
        }

        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();
        BronzeContent cached = contentCache.get(uri);

        // ALLOW_CACHED_ONLY: return cached state only, never fetch externally
        if (decision.type() == AccessDecisionType.ALLOW_CACHED_ONLY) {
            if (cached != null) {
                return MediatedResult.success(cached, decision);
            }
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.CACHE_ONLY_ALLOWED,
                    "No cached content available and external fetch not permitted"));
        }

        // ALLOW: return cached if present
        if (cached != null) {
            return MediatedResult.success(cached, decision);
        }

        // Cache miss: check if external acquisition is allowed (Fix 2)
        ResourceAccessRequest fetchRequest = new ResourceAccessRequest(
                request.actor(), uri, ResourceOperation.FETCH_EXTERNAL, request.purpose());
        ResourceAccessDecision fetchDecision = accessPolicy.evaluate(fetchRequest);
        if (!fetchDecision.isAllowed()) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    fetchDecision.reasonCode(),
                    "External acquisition denied: " + fetchDecision.explanation()));
        }

        // Acquire internally
        try {
            BronzeContent acquired = acquisitionPort.fetchContent(uri);
            contentCache.put(uri, acquired);
            return MediatedResult.success(acquired, decision);
        } catch (IOException e) {
            return MediatedResult.error("Acquisition failed: " + e.getMessage());
        }
    }

    /**
     * Deletes a bronze archive entry (tombstones it).
     *
     * <p>The request must carry {@link ResourceOperation#DELETE_ARCHIVE_ENTRY}.
     * This removes the cached state and the archive snapshot (URI-based, type-agnostic).
     * User-specific denial does NOT call this method — only explicit
     * blacklist/tombstone policy does.
     *
     * @param request the access request (operation must be DELETE_ARCHIVE_ENTRY)
     * @return the result
     */
    public MediatedResult<Void> deleteEntry(ResourceAccessRequest request) {
        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.DELETE_ARCHIVE_ENTRY) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.UNKNOWN_RESOURCE,
                    "Operation mismatch: deleteEntry requires DELETE_ARCHIVE_ENTRY, got " + request.operation()));
        }

        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();
        listingCache.remove(uri);
        contentCache.remove(uri);
        metadataCache.remove(uri);

        // Fix 5: Remove from archive by URI (type-agnostic)
        archive.removeByUri(uri);

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
