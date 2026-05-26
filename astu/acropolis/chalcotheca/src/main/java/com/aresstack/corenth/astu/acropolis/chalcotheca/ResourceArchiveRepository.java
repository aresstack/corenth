package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.List;

/**
 * Persistence port for {@link ArchivedResource} lifecycle records.
 *
 * <p>This interface abstracts how archived resources are stored. Implementations
 * may use in-memory maps, filesystem JSON, relational databases, or any other
 * backend — the chalcotheca core does not prescribe a specific technology.
 *
 * <p>Unlike {@link ResourceArchive} (which focuses on snapshot-level change
 * detection), this repository manages the full lifecycle of archived resources
 * including state transitions and tombstoning.
 */
public interface ResourceArchiveRepository {

    /**
     * Saves or updates an archived resource record.
     *
     * @param resource the resource to persist
     */
    void save(ArchivedResource resource);

    /**
     * Retrieves the archived resource for the given reference, or {@code null}
     * if not tracked.
     *
     * @param ref the resource reference
     * @return the archived resource, or {@code null}
     */
    ArchivedResource findByRef(VirtualResourceRef ref);

    /**
     * Returns all resources currently in the given lifecycle state.
     *
     * @param state the lifecycle state to filter by
     * @return a list of matching resources (never {@code null})
     */
    List<ArchivedResource> findByState(ResourceLifecycleState state);

    /**
     * Removes the archived resource record entirely.
     *
     * <p>Use {@link ArchivedResource#tombstone(long)} for soft-delete semantics;
     * this method performs a hard delete from the repository.
     *
     * @param ref the resource reference to remove
     * @return {@code true} if a record was removed, {@code false} if not found
     */
    boolean remove(VirtualResourceRef ref);
}
