package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Port interface for lexical full-text indexing and retrieval.
 *
 * <p>Implementations provide classical keyword/full-text search over indexed
 * documents. The interface is technology-agnostic — consumers do not need to
 * know whether Lucene, Elasticsearch, or another engine backs the index.
 *
 * <p>This interface uses an explicit commit model: callers must invoke
 * {@link #commit()} after write operations before changes become visible
 * to {@link #search(LexicalQuery)}.
 *
 * <p>Adapted from MainframeMate's {@code LexicalIndex} port interface, with
 * Corenth resource identity and lifecycle management replacing the original's
 * singleton design.
 */
public interface LexicalIndex extends Closeable {

    /**
     * Indexes a document. If a document with the same resource reference already
     * exists, it is replaced.
     *
     * <p>Changes are not visible to search until {@link #commit()} is called.
     *
     * @param document the document to index
     * @throws IOException if the index cannot be written
     */
    void index(LexicalDocument document) throws IOException;

    /**
     * Searches the index for documents matching the given query.
     *
     * <p>Only committed changes are visible. Call {@link #commit()} after
     * write operations to ensure new/updated documents appear in results.
     *
     * @param query the search query
     * @return a list of results ordered by relevance (highest score first),
     *         or an empty list if no matches are found
     * @throws IOException if the index cannot be read
     */
    List<LexicalSearchResult> search(LexicalQuery query) throws IOException;

    /**
     * Removes all indexed content for the given resource reference.
     *
     * <p>Changes are not visible to search until {@link #commit()} is called.
     *
     * @param resourceRef the resource to remove
     * @throws IOException if the index cannot be written
     */
    void remove(VirtualResourceRef resourceRef) throws IOException;

    /**
     * Commits any pending changes to the index, making them visible to search.
     *
     * @throws IOException if the commit fails
     */
    void commit() throws IOException;
}
