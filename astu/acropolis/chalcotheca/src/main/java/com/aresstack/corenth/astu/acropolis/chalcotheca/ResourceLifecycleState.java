package com.aresstack.corenth.astu.acropolis.chalcotheca;

/**
 * Lifecycle state of a resource within the chalcotheca archive.
 *
 * <p>A resource progresses through these states as it moves from initial
 * acquisition to full indexing, and may transition to stale or tombstoned
 * when it is no longer current or has been removed at the source.
 */
public enum ResourceLifecycleState {

    /** Detected by a connector but not yet acquired. */
    PENDING,

    /** Content has been acquired (downloaded/fetched) and is available for processing. */
    ACQUIRED,

    /** Content has been cached and a digest computed; awaiting indexing. */
    CACHED,

    /** Fully processed: cached, indexed and searchable. */
    INDEXED,

    /** Previously indexed but the source content has changed; awaiting re-processing. */
    STALE,

    /** Removed at source; retained as a tombstone for downstream cleanup. */
    TOMBSTONED
}
