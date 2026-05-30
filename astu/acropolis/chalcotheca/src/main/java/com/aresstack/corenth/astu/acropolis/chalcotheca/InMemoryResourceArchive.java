package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * In-memory implementation of {@link ResourceArchive}.
 *
 * <p>Suitable for tests and the walking skeleton. A filesystem-backed
 * implementation can be added later without changing the interface contract.
 */
public final class InMemoryResourceArchive implements ResourceArchive {

    private final Map<VirtualResourceRef, ResourceSnapshot> snapshots =
            new HashMap<VirtualResourceRef, ResourceSnapshot>();

    @Override
    public void store(ResourceSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        snapshots.put(snapshot.ref(), snapshot);
    }

    @Override
    public ResourceSnapshot find(VirtualResourceRef ref) {
        if (ref == null) {
            return null;
        }
        return snapshots.get(ref);
    }

    @Override
    public boolean hasChanged(VirtualResourceRef ref, ResourceDigest digest) {
        ResourceSnapshot existing = find(ref);
        if (existing == null) {
            return true; // never seen before
        }
        return !existing.digest().equals(digest);
    }

    @Override
    public boolean remove(VirtualResourceRef ref) {
        return snapshots.remove(ref) != null;
    }

    @Override
    public boolean removeByUri(BookmarkUri uri) {
        if (uri == null) return false;
        boolean removed = false;
        Iterator<Map.Entry<VirtualResourceRef, ResourceSnapshot>> it = snapshots.entrySet().iterator();
        while (it.hasNext()) {
            if (uri.equals(it.next().getKey().uri())) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public ResourceSnapshot findByUri(BookmarkUri uri) {
        if (uri == null) return null;
        for (Map.Entry<VirtualResourceRef, ResourceSnapshot> entry : snapshots.entrySet()) {
            if (uri.equals(entry.getKey().uri())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
