package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Provide a deterministic {@link Reranker} for contract tests.
 *
 * <p>The score is the fraction of distinct query words that occur in the passage. It stands
 * in for a cross-encoder in tests only and proves nothing about real reranking quality.
 */
public final class TokenOverlapReranker implements Reranker {

    private final List<RerankRequest> requests = new ArrayList<RerankRequest>();
    private RerankException nextFailure;

    /** Make the next {@link #rerank} call fail with the given exception. */
    public void failNextCall(RerankException failure) {
        this.nextFailure = failure;
    }

    /** Return every request received so far. */
    public List<RerankRequest> requests() {
        return new ArrayList<RerankRequest>(requests);
    }

    @Override
    public List<RerankedResult> rerank(RerankRequest request) throws RerankException {
        requests.add(request);
        if (nextFailure != null) {
            RerankException failure = nextFailure;
            nextFailure = null;
            throw failure;
        }
        Set<String> queryWords = words(request.query());
        List<RerankedResult> results = new ArrayList<RerankedResult>();
        for (RerankCandidate candidate : request.candidates()) {
            Set<String> passageWords = words(candidate.text());
            int matches = 0;
            for (String word : queryWords) {
                if (passageWords.contains(word)) {
                    matches++;
                }
            }
            results.add(new RerankedResult(candidate.key(),
                    queryWords.isEmpty() ? 0d : (double) matches / queryWords.size()));
        }
        return results;
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<String>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                words.add(token);
            }
        }
        return words;
    }
}
