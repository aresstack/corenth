package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

/**
 * What happened to a resource's content between its Chalcotheca record and the current
 * observation.
 *
 * <p>This is a statement about the content only. It says nothing about the indexed-version fact
 * or caches: {@link #UNCHANGED} does not mean "nothing to do"; whether derivatives are retained,
 * refreshed, reindexed or withdrawn is decided separately by the derivative disposition.
 */
public enum ChangeKind {
    /** No record exists and the resource is present at the source. */
    NEW,
    /** The observed content equals the latest observed version. */
    UNCHANGED,
    /** The observed content differs from the latest observed version. */
    CHANGED,
    /** A record exists and the resource is absent at the source. */
    REMOVED,
    /** Neither a record exists nor is the resource present at the source. */
    NOT_FOUND
}
