package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * A snapshot record for a resource that has been processed.
 *
 * <p>Stores enough information to determine whether a resource needs reindexing.
 */
public final class ResourceSnapshot {

    private final VirtualResourceRef ref;
    private final ResourceDigest digest;
    private final long indexedAtMillis;

    public ResourceSnapshot(VirtualResourceRef ref, ResourceDigest digest, long indexedAtMillis) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (digest == null) {
            throw new IllegalArgumentException("digest must not be null");
        }
        this.ref = ref;
        this.digest = digest;
        this.indexedAtMillis = indexedAtMillis;
    }

    /** Returns the resource reference. */
    public VirtualResourceRef ref() {
        return ref;
    }

    /** Returns the digest at the time of indexing. */
    public ResourceDigest digest() {
        return digest;
    }

    /** Returns the timestamp when the resource was last indexed. */
    public long indexedAtMillis() {
        return indexedAtMillis;
    }

    @Override
    public String toString() {
        return "ResourceSnapshot{" + ref + ", " + digest + "}";
    }
}
