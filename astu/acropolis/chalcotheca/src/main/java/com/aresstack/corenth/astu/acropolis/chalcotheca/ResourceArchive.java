package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * Port for storing and retrieving resource snapshots.
 *
 * <p>The archive tracks which resources have been processed and their
 * digest at the time of processing, allowing the system to detect changes
 * and avoid unnecessary reindexing.
 */
public interface ResourceArchive {

    /**
     * Stores or updates a snapshot for the given resource.
     *
     * @param snapshot the snapshot to store
     */
    void store(ResourceSnapshot snapshot);

    /**
     * Retrieves the most recent snapshot for the given resource, or {@code null}
     * if the resource has not been seen before.
     *
     * @param ref the resource reference
     * @return the stored snapshot, or {@code null}
     */
    ResourceSnapshot find(VirtualResourceRef ref);

    /**
     * Returns {@code true} if the resource content has changed since the last snapshot.
     *
     * @param ref    the resource reference
     * @param digest the current digest
     * @return {@code true} if reindexing is needed
     */
    boolean hasChanged(VirtualResourceRef ref, ResourceDigest digest);

    /**
     * Removes the snapshot for the given resource.
     *
     * <p>Used when a resource is tombstoned or permanently deleted.
     *
     * @param ref the resource reference to remove
     * @return {@code true} if a snapshot was removed, {@code false} if not found
     */
    boolean remove(VirtualResourceRef ref);

    /**
     * Removes all snapshots matching the given bookmark URI, regardless of resource kind.
     *
     * <p>Used by the mediated resource service when the resource kind is not known.
     *
     * @param uri the bookmark URI to remove
     * @return {@code true} if at least one snapshot was removed
     */
    boolean removeByUri(BookmarkUri uri);

    /**
     * Finds a snapshot by bookmark URI, regardless of resource kind.
     *
     * @param uri the bookmark URI
     * @return the stored snapshot, or {@code null}
     */
    ResourceSnapshot findByUri(BookmarkUri uri);
}
