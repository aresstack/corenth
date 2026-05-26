package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * Port for evaluating indexing policy on a resource.
 *
 * <p>Implementations decide whether a resource should be accepted for
 * processing/indexing based on configurable rules (include/exclude patterns,
 * size limits, scheme restrictions, etc.).
 */
public interface ResourcePolicy {

    /**
     * Evaluates whether the given resource should be accepted.
     *
     * @param ref       the resource reference
     * @param sizeBytes the content size in bytes
     * @return the policy decision with reason
     */
    PolicyReason evaluate(VirtualResourceRef ref, long sizeBytes);
}
