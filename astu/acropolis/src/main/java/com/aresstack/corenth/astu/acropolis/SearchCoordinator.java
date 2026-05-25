package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalQuery;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * A thin search facade over the lexical index.
 *
 * <p>Provides a simple entry point for searching indexed resources
 * without exposing Lucene internals to callers.
 */
public final class SearchCoordinator {

    private final LexicalIndex lexicalIndex;

    public SearchCoordinator(LexicalIndex lexicalIndex) {
        if (lexicalIndex == null) {
            throw new IllegalArgumentException("lexicalIndex must not be null");
        }
        this.lexicalIndex = lexicalIndex;
    }

    /**
     * Searches the lexical index with the given query text.
     *
     * @param queryText  the search text
     * @param maxResults maximum number of results
     * @return the search results (may be empty)
     * @throws IOException if the index cannot be read
     */
    public List<LexicalSearchResult> search(String queryText, int maxResults) throws IOException {
        if (queryText == null || queryText.trim().isEmpty()) {
            return Collections.emptyList();
        }
        LexicalQuery query = new LexicalQuery(queryText, maxResults);
        return lexicalIndex.search(query);
    }
}
