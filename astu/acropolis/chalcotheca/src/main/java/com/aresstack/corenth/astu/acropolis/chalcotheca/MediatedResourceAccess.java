package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope.ResourceSizePolicy;

/**
 * Narrow mediated-access contract through which lifecycle use cases obtain bronze resources.
 *
 * <p>This is the only contract the Acropolis lifecycle may use to read or list resources.
 * It is implemented by {@link MediatedResourceService}, the archive counter, which consults
 * Tamias for every request and acquires missing content internally through the
 * {@link AcquisitionPort}. Callers never see connectors, the acquisition port or secrets;
 * they receive a {@link MediatedResult} that is either a bronze payload, a typed Tamias
 * decision that withholds the payload, or an acquisition error.
 *
 * <p>Keeping the lifecycle on this interface instead of the concrete service lets the
 * composition point (see {@code docs/adr/0001-composition-root.md}) decide how the counter
 * is built, and keeps {@code acropolis} independent of the service's internal caches and of
 * administrative operations such as {@code deleteEntry}.
 */
public interface MediatedResourceAccess {

    /**
     * Reads the content of a resource, mediated by Tamias.
     *
     * @param request access request carrying actor, target and
     *                {@link com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation#READ_CONTENT}
     * @return the mediated result; never {@code null}
     */
    MediatedResult<BronzeContent> readContent(ResourceAccessRequest request);

    /**
     * Lists the children of a container resource, mediated by Tamias.
     *
     * @param request access request carrying actor, target and
     *                {@link com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation#LIST_CHILDREN}
     * @return the mediated result; never {@code null}
     */
    MediatedResult<BronzeListing> listChildren(ResourceAccessRequest request);

    /**
     * Reads the content of a resource and refreshes it from the source when Tamias permits
     * (#10 Slice 5).
     *
     * <p>The request carries {@code READ_CONTENT}. The counter evaluates it, then asks Tamias for
     * {@code REFRESH_EXTERNAL}: on {@code ALLOW} or {@code REQUIRE_AUTH} it acquires the payload
     * again (with access preparation) and replaces its cached payload; otherwise it behaves like
     * {@link #readContent(ResourceAccessRequest)}. A confirmed absence at the source is the typed
     * failure {@link MediatedResult.Failure#SOURCE_ABSENT}.
     *
     * <p>Every acquisition the call makes is bounded by the Tamias size policy: with a limit the
     * counter reads at most one byte beyond it and never caches an oversized payload; it withholds
     * the payload with {@code TOO_LARGE} instead. A cached payload is returned as it is; its size
     * is the caller's to check.
     *
     * @param request          access request carrying actor, target and {@code READ_CONTENT}
     * @param acquisitionLimit the Tamias size policy that bounds the acquisition
     * @return the mediated result; never {@code null}
     */
    MediatedResult<BronzeContent> refreshContent(ResourceAccessRequest request, ResourceSizePolicy acquisitionLimit);

    /**
     * Reads source metadata without acquiring the payload, mediated by Tamias (#10 Slice 5).
     *
     * <p>The request carries {@code READ_METADATA}; the source access is gated like a content
     * acquisition by {@code FETCH_EXTERNAL}. Sources without metadata yield
     * {@link MediatedResult.Failure#METADATA_UNAVAILABLE}; a confirmed absence yields
     * {@link MediatedResult.Failure#SOURCE_ABSENT}.
     *
     * @param request access request carrying actor, target and {@code READ_METADATA}
     * @return the mediated result; never {@code null}
     */
    MediatedResult<BronzeMetadata> readMetadata(ResourceAccessRequest request);

    /**
     * Drops the counter's cached payloads (content, listing, metadata) for a resource.
     *
     * <p>This executes a Tamias cache disposition ({@code INVALIDATE}); it exposes nothing and
     * leaves the resource records untouched.
     *
     * @param uri the resource whose cached payloads to drop
     */
    void invalidatePayload(BookmarkUri uri);
}
