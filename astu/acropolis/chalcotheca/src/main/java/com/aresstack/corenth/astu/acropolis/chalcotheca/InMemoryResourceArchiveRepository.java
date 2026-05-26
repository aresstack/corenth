package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory implementation of {@link ResourceArchiveRepository}.
 *
 * <p>Suitable for tests and the walking skeleton. Production implementations
 * may back this with filesystem, database or other persistent stores.
 */
public final class InMemoryResourceArchiveRepository implements ResourceArchiveRepository {

    private final Map<VirtualResourceRef, ArchivedResource> resources =
            new HashMap<VirtualResourceRef, ArchivedResource>();

    @Override
    public void save(ArchivedResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
        resources.put(resource.ref(), resource);
    }

    @Override
    public ArchivedResource findByRef(VirtualResourceRef ref) {
        if (ref == null) {
            return null;
        }
        return resources.get(ref);
    }

    @Override
    public List<ArchivedResource> findByState(ResourceLifecycleState state) {
        List<ArchivedResource> result = new ArrayList<ArchivedResource>();
        for (ArchivedResource r : resources.values()) {
            if (r.state() == state) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public boolean remove(VirtualResourceRef ref) {
        return resources.remove(ref) != null;
    }
}
