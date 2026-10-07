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
 * to Tamias before invoking the internal {@link AcquisitionPort}. {@code ALLOW} and
 * {@code REQUIRE_AUTH} permit acquisition; the {@link AcquisitionAccessPort} then prepares it
 * (#10 Slice 3). Unauthenticated sources need no capability and never reach the vault; an
 * authenticated source receives an opaque {@link AcquisitionCapability} that the counter passes
 * to the port and closes afterwards. Cancellation, missing credentials and authentication
 * failure are typed {@link MediatedResult.Failure}s, distinct from policy denial.
 *
 * <p>Lifecycle use cases depend on the {@link MediatedResourceAccess} contract that this
 * service implements, not on this class, so that the composition point decides how the
 * counter is assembled.
 *
 * <p><strong>Payload caches (#10 Slice 5):</strong> the three in-memory stores below hold
 * payloads without TTL. {@link #readContent(ResourceAccessRequest)} serves cached content;
 * {@link #refreshContent(ResourceAccessRequest)} re-acquires it when Tamias permits
 * {@code REFRESH_EXTERNAL}; {@link #invalidatePayload(BookmarkUri)} executes a Tamias cache
 * disposition (#5) and {@link #deleteEntry(ResourceAccessRequest)} an explicit tombstone.
 * The {@link ResourceArchive} facade and the resource records (#33) hold facts about payloads,
 * not the payloads, and they do not record cache presence.
 */
public final class MediatedResourceService implements MediatedResourceAccess {

    private final ResourceAccessPolicy accessPolicy;
    private final AcquisitionPort acquisitionPort;
    private final ResourceArchive archive;
    private final AcquisitionAccessPort accessPreparation;

    // In-memory bronze state stores without TTL or invalidation; see the known-limitation note
    // in the class Javadoc (consolidated against the #33 record/version contract and #5 invalidation).
    private final Map<BookmarkUri, BronzeListing> listingCache = new ConcurrentHashMap<BookmarkUri, BronzeListing>();
    private final Map<BookmarkUri, BronzeContent> contentCache = new ConcurrentHashMap<BookmarkUri, BronzeContent>();
    private final Map<BookmarkUri, BronzeMetadata> metadataCache = new ConcurrentHashMap<BookmarkUri, BronzeMetadata>();

    /**
     * Creates a counter for compositions without authenticated sources.
     *
     * @see AcquisitionAccessPort#unauthenticated()
     */
    public MediatedResourceService(ResourceAccessPolicy accessPolicy,
                                   AcquisitionPort acquisitionPort,
                                   ResourceArchive archive) {
        this(accessPolicy, acquisitionPort, archive, AcquisitionAccessPort.unauthenticated());
    }

    /**
     * Creates a counter that prepares every external acquisition through the given access port
     * (#10 Slice 3).
     */
    public MediatedResourceService(ResourceAccessPolicy accessPolicy,
                                   AcquisitionPort acquisitionPort,
                                   ResourceArchive archive,
                                   AcquisitionAccessPort accessPreparation) {
        if (accessPolicy == null) throw new IllegalArgumentException("accessPolicy must not be null");
        if (acquisitionPort == null) throw new IllegalArgumentException("acquisitionPort must not be null");
        if (archive == null) throw new IllegalArgumentException("archive must not be null");
        if (accessPreparation == null) throw new IllegalArgumentException("accessPreparation must not be null");
        this.accessPolicy = accessPolicy;
        this.acquisitionPort = acquisitionPort;
        this.archive = archive;
        this.accessPreparation = accessPreparation;
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
    @Override
    public MediatedResult<BronzeListing> listChildren(ResourceAccessRequest request) {
        if (request == null) {
            return MediatedResult.error("request must not be null");
        }

        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.LIST_CHILDREN) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.INVALID_OPERATION,
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

        // Cache miss: a separate FETCH_EXTERNAL decision, access preparation, then acquisition
        final BookmarkUri target = uri;
        return acquireExternally(request, decision, new Acquisition<BronzeListing>() {
            @Override
            public BronzeListing acquire(AcquisitionCapability capability) throws IOException {
                BronzeListing acquired = acquisitionPort.listChildren(target, capability);
                listingCache.put(target, acquired);
                return acquired;
            }
        });
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
    @Override
    public MediatedResult<BronzeContent> readContent(ResourceAccessRequest request) {
        if (request == null) {
            return MediatedResult.error("request must not be null");
        }

        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.READ_CONTENT) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.INVALID_OPERATION,
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

        // Cache miss: a separate FETCH_EXTERNAL decision, access preparation, then acquisition
        final BookmarkUri target = uri;
        return acquireExternally(request, decision, new Acquisition<BronzeContent>() {
            @Override
            public BronzeContent acquire(AcquisitionCapability capability) throws IOException {
                BronzeContent acquired = acquisitionPort.fetchContent(target, capability);
                contentCache.put(target, acquired);
                return acquired;
            }
        });
    }

    /**
     * Reads content and refreshes it from the source when Tamias permits {@code REFRESH_EXTERNAL}
     * (#10 Slice 5).
     *
     * <p>A permitted refresh bypasses the cached payload, prepares access like any acquisition and
     * replaces the cache on success. A failed refresh keeps the previous cached payload; the caller
     * receives the typed failure, never the stale bytes. Without refresh permission the request is
     * served exactly like {@link #readContent(ResourceAccessRequest)}.
     */
    @Override
    public MediatedResult<BronzeContent> refreshContent(ResourceAccessRequest request) {
        if (request == null) {
            return MediatedResult.error("request must not be null");
        }
        if (request.operation() != ResourceOperation.READ_CONTENT) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.INVALID_OPERATION,
                    "Operation mismatch: refreshContent requires READ_CONTENT, got " + request.operation()));
        }
        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }
        if (decision.type() == AccessDecisionType.ALLOW_CACHED_ONLY) {
            return readContent(request);
        }
        ResourceAccessDecision refreshDecision = evaluateSourceAccess(request, ResourceOperation.REFRESH_EXTERNAL);
        if (!permitsSourceAccess(refreshDecision)) {
            return readContent(request);
        }
        final BookmarkUri target = request.target();
        return acquirePrepared(request, decision, refreshDecision, new Acquisition<BronzeContent>() {
            @Override
            public BronzeContent acquire(AcquisitionCapability capability) throws IOException {
                BronzeContent acquired = acquisitionPort.fetchContent(target, capability);
                contentCache.put(target, acquired);
                return acquired;
            }
        });
    }

    /**
     * Reads source metadata, mediated by Tamias (#10 Slice 5).
     *
     * <p>The request must carry {@link ResourceOperation#READ_METADATA}. Metadata is always read
     * from the source (gated by {@code FETCH_EXTERNAL} and access preparation) and kept as the last
     * known metadata of the URI until {@link #invalidatePayload(BookmarkUri)} or
     * {@link #deleteEntry(ResourceAccessRequest)} drops it.
     */
    @Override
    public MediatedResult<BronzeMetadata> readMetadata(ResourceAccessRequest request) {
        if (request == null) {
            return MediatedResult.error("request must not be null");
        }
        if (request.operation() != ResourceOperation.READ_METADATA) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.INVALID_OPERATION,
                    "Operation mismatch: readMetadata requires READ_METADATA, got " + request.operation()));
        }
        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }
        final BookmarkUri target = request.target();
        if (!acquisitionPort.offersMetadata(target)) {
            return MediatedResult.failure(MediatedResult.Failure.METADATA_UNAVAILABLE,
                    "The source offers no metadata");
        }
        if (decision.type() == AccessDecisionType.ALLOW_CACHED_ONLY) {
            BronzeMetadata cached = metadataCache.get(target);
            if (cached != null) {
                return MediatedResult.success(cached, decision);
            }
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.CACHE_ONLY_ALLOWED,
                    "No cached metadata available and external fetch not permitted"));
        }
        return acquireExternally(request, decision, new Acquisition<BronzeMetadata>() {
            @Override
            public BronzeMetadata acquire(AcquisitionCapability capability) throws IOException {
                BronzeMetadata metadata = acquisitionPort.fetchMetadata(target, capability);
                if (metadata == null) {
                    throw new MetadataUnavailableException();
                }
                metadataCache.put(target, metadata);
                return metadata;
            }
        });
    }

    /**
     * Drops the cached payloads of a URI (content, listing, metadata). The resource records stay
     * untouched; this only executes a cache disposition.
     */
    @Override
    public void invalidatePayload(BookmarkUri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        listingCache.remove(uri);
        contentCache.remove(uri);
        metadataCache.remove(uri);
    }

    /**
     * Deletes a bronze archive entry (tombstones it).
     *
     * <p>The request must carry {@link ResourceOperation#DELETE_ARCHIVE_ENTRY}.
     * This removes the cached payload state and records a removal at the source for every
     * archive record with the URI (type-agnostic). The records' version histories are kept,
     * and an indexed-version fact is not withdrawn: derived indexes are untouched here, and
     * withdrawing them is decided by Tamias (#5) and executed by Acropolis (#10).
     * User-specific denial does NOT call this method — only explicit
     * blacklist/tombstone policy does.
     *
     * @param request the access request (operation must be DELETE_ARCHIVE_ENTRY)
     * @return the result
     */
    public MediatedResult<Void> deleteEntry(ResourceAccessRequest request) {
        if (request == null) {
            return MediatedResult.error("request must not be null");
        }

        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.DELETE_ARCHIVE_ENTRY) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.INVALID_OPERATION,
                    "Operation mismatch: deleteEntry requires DELETE_ARCHIVE_ENTRY, got " + request.operation()));
        }

        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (decision.type() != AccessDecisionType.ALLOW) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();
        listingCache.remove(uri);
        contentCache.remove(uri);
        metadataCache.remove(uri);

        // Fix 5: Tombstone the archive records by URI (type-agnostic)
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

    /**
     * Acquires a missing payload externally after a separate {@code FETCH_EXTERNAL} decision.
     *
     * <p>Only {@code ALLOW} and {@code REQUIRE_AUTH} permit acquisition; {@code ALLOW_CACHED_ONLY},
     * {@code REQUIRE_SOURCE_CHECK} and {@code DENY} block it. The access port is asked for every
     * permitted acquisition; it answers {@code NOT_REQUIRED} for unauthenticated sources without
     * contacting the vault. {@code REQUIRE_AUTH} without a granted capability withholds the
     * payload. A granted capability is closed after the acquisition, also on failure.
     */
    private <T> MediatedResult<T> acquireExternally(ResourceAccessRequest request,
                                                    ResourceAccessDecision decision,
                                                    Acquisition<T> acquisition) {
        ResourceAccessDecision fetchDecision = evaluateSourceAccess(request, ResourceOperation.FETCH_EXTERNAL);
        if (!permitsSourceAccess(fetchDecision)) {
            return MediatedResult.denied(fetchDecision);
        }
        return acquirePrepared(request, decision, fetchDecision, acquisition);
    }

    /** Asks Tamias whether the source may be contacted for the given external operation. */
    private ResourceAccessDecision evaluateSourceAccess(ResourceAccessRequest request, ResourceOperation operation) {
        return accessPolicy.evaluate(new ResourceAccessRequest(
                request.actor(), request.target(), operation, request.purpose()));
    }

    private static boolean permitsSourceAccess(ResourceAccessDecision decision) {
        return decision.type() == AccessDecisionType.ALLOW || decision.type() == AccessDecisionType.REQUIRE_AUTH;
    }

    /** Prepares access for a permitted source contact and runs the acquisition. */
    private <T> MediatedResult<T> acquirePrepared(ResourceAccessRequest request,
                                                  ResourceAccessDecision decision,
                                                  ResourceAccessDecision sourceDecision,
                                                  Acquisition<T> acquisition) {
        BookmarkUri uri = request.target();
        AcquisitionAccess access;
        try {
            access = accessPreparation.prepare(new AcquisitionAccessRequest(
                    uri, request.operation(), request.actor(), request.purpose()));
        } catch (RuntimeException e) {
            return MediatedResult.error("Access preparation failed: " + e.getMessage());
        }
        if (access == null) {
            return MediatedResult.error("Access preparation returned no result");
        }

        switch (access.status()) {
            case NOT_REQUIRED:
                if (sourceDecision.type() == AccessDecisionType.REQUIRE_AUTH) {
                    // Tamias requires authentication, but no station can provide it for this source
                    return MediatedResult.denied(sourceDecision);
                }
                return acquireWith(null, decision, acquisition);
            case GRANTED:
                try {
                    return acquireWith(access.capability(), decision, acquisition);
                } finally {
                    access.close();
                }
            case UNAVAILABLE:
                return MediatedResult.failure(MediatedResult.Failure.AUTHENTICATION_UNAVAILABLE, access.detail());
            case CANCELLED:
                return MediatedResult.failure(MediatedResult.Failure.AUTHENTICATION_CANCELLED, access.detail());
            case DENIED:
                return MediatedResult.failure(MediatedResult.Failure.AUTHENTICATION_DENIED, access.detail());
            case FAILED:
            default:
                return MediatedResult.failure(MediatedResult.Failure.AUTHENTICATION_FAILED, access.detail());
        }
    }

    private static <T> MediatedResult<T> acquireWith(AcquisitionCapability capability,
                                                     ResourceAccessDecision decision,
                                                     Acquisition<T> acquisition) {
        try {
            T acquired = acquisition.acquire(capability);
            if (acquired == null) {
                return MediatedResult.error("Acquisition returned no payload");
            }
            return MediatedResult.success(acquired, decision);
        } catch (MetadataUnavailableException e) {
            return MediatedResult.failure(MediatedResult.Failure.METADATA_UNAVAILABLE, e.getMessage());
        } catch (SourceAbsentException e) {
            return MediatedResult.failure(MediatedResult.Failure.SOURCE_ABSENT, e.getMessage());
        } catch (IOException e) {
            return MediatedResult.error("Acquisition failed: " + e.getMessage());
        }
    }

    /** Signals that the acquisition port offers no metadata for the source. */
    private static final class MetadataUnavailableException extends IOException {
        private static final long serialVersionUID = 1L;

        MetadataUnavailableException() {
            super("The source offers no metadata");
        }
    }

    /** One acquisition through the internal port that also fills the matching cache. */
    private interface Acquisition<T> {
        T acquire(AcquisitionCapability capability) throws IOException;
    }
}
