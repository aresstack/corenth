package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai;

/**
 * A query against the lexical index.
 *
 * <p>Encapsulates the search text and result constraints such as maximum
 * number of results. The query is expressed in natural language / keyword
 * form and will be parsed by the underlying full-text engine.
 */
public final class LexicalQuery {

    private final String queryText;
    private final int maxResults;

    /**
     * Creates a lexical query.
     *
     * @param queryText  the search text
     * @param maxResults maximum number of results to return
     */
    public LexicalQuery(String queryText, int maxResults) {
        if (queryText == null || queryText.trim().isEmpty()) {
            throw new IllegalArgumentException("queryText must not be null or blank");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be at least 1");
        }
        this.queryText = queryText;
        this.maxResults = maxResults;
    }

    /**
     * Creates a query with a default limit of 10 results.
     *
     * @param queryText the search text
     */
    public LexicalQuery(String queryText) {
        this(queryText, 10);
    }

    /** Returns the search text. */
    public String queryText() {
        return queryText;
    }

    /** Returns the maximum number of results. */
    public int maxResults() {
        return maxResults;
    }

    @Override
    public String toString() {
        return "LexicalQuery{'" + queryText + "', max=" + maxResults + "}";
    }
}
