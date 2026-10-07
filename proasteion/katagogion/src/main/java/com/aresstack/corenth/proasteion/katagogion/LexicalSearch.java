package com.aresstack.corenth.proasteion.katagogion;

/**
 * Tool capability for lexical search over already indexed resources.
 *
 * <p>Implementations adapt the existing Acropolis search use case; tools never see the index
 * technology behind it.
 */
public interface LexicalSearch {

    /**
     * Search indexed resources.
     *
     * @param queryText non-blank search text
     * @param maxHits   positive upper bound for the number of hits
     * @return the typed outcome; never {@code null}
     */
    SearchOutcome search(String queryText, int maxHits);
}
