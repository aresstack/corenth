package com.aresstack.corenth.proasteion.katagogion.mediation;

import com.aresstack.corenth.astu.acropolis.SearchCoordinator;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.proasteion.katagogion.LexicalSearch;
import com.aresstack.corenth.proasteion.katagogion.SearchHit;
import com.aresstack.corenth.proasteion.katagogion.SearchOutcome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lexical search capability backed by the existing Acropolis {@link SearchCoordinator} use case.
 *
 * <p>The adapter translates index results into tool-facing {@link SearchHit}s and an unreadable
 * index into {@link SearchOutcome#unavailable}; no index type reaches the tool.
 */
public final class SearchCoordinatorLexicalSearch implements LexicalSearch {

    private final SearchCoordinator searchCoordinator;

    public SearchCoordinatorLexicalSearch(SearchCoordinator searchCoordinator) {
        if (searchCoordinator == null) {
            throw new IllegalArgumentException("searchCoordinator must not be null");
        }
        this.searchCoordinator = searchCoordinator;
    }

    @Override
    public SearchOutcome search(String queryText, int maxHits) {
        List<LexicalSearchResult> results;
        try {
            results = searchCoordinator.search(queryText, maxHits);
        } catch (IOException e) {
            return SearchOutcome.unavailable("lexical index unavailable");
        }
        List<SearchHit> hits = new ArrayList<SearchHit>(results.size());
        for (LexicalSearchResult result : results) {
            hits.add(new SearchHit(result.resourceRef(), result.title(), result.excerpt(),
                    result.score(), result.chunkIndex()));
        }
        return SearchOutcome.hits(hits);
    }
}
