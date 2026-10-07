package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition;

/**
 * What the orchestration has to do with transient derivatives of a resource's payload
 * (content/listing/metadata caches, extracted text).
 *
 * <p>The action holds for whatever cache entry may exist; Tamias does not know or store whether
 * one exists. Executing the action, including finding out what is cached, is #10.
 */
public enum CacheAction {
    /** Cached derivatives match the current content and may be served. */
    RETAIN,
    /**
     * Cached derivatives cannot be trusted for the current content (it changed, or no recorded
     * version exists to validate them); drop them and derive again from the current observation.
     */
    REFRESH,
    /** The resource is gone at its source; drop cached derivatives without replacement. */
    INVALIDATE
}
