package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition;

/**
 * What the orchestration has to do with the derived index entries (lexical and later semantic)
 * of a resource.
 */
public enum IndexAction {
    /** Build index entries for a resource that has never been indexed. */
    INDEX,
    /** Rebuild index entries because the indexed fact is missing or does not match the content. */
    REINDEX,
    /** The indexed version matches the current content; keep the index entries. */
    RETAIN,
    /** The resource is gone at its source but still indexed; withdraw its index entries. */
    WITHDRAW,
    /** Nothing is indexed and nothing has to be indexed. */
    NONE
}
