package com.aresstack.corenth.astu;

/**
 * High-level classification of a virtual resource by its logical nature.
 *
 * <p>This distinguishes what a resource conceptually is — a document, source code,
 * a message, a dataset — regardless of which backend or protocol delivers it.
 */
public enum VirtualResourceKind {

    DOCUMENT,
    SOURCE_CODE,
    MESSAGE,
    DATASET,
    CONFIGURATION,
    MEDIA,
    OTHER
}
