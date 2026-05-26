package com.aresstack.corenth.astu.acropolis.chalcotheca;

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
}
