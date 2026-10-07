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
     * Sentinel passed as {@code sizeBytes} when the policy is evaluated before the content
     * has been acquired and its size is therefore not yet known. Implementations must not
     * treat it as an empty resource; size limits are enforced in a second evaluation with the
     * real size.
     */
    long SIZE_UNKNOWN = -1L;

    /**
     * Evaluates whether the given resource should be accepted.
     *
     * @param ref       the resource reference
     * @param sizeBytes the content size in bytes, or {@link #SIZE_UNKNOWN} when evaluated
     *                  before acquisition
     * @return the policy decision with reason
     */
    PolicyReason evaluate(VirtualResourceRef ref, long sizeBytes);
}
