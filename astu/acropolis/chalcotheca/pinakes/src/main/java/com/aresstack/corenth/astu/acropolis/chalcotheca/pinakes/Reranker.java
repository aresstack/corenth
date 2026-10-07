package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.List;

/**
 * Port for rescoring candidate passages against a query, typically with a cross-encoder.
 *
 * <p>Reranking is independent of embeddings: it reads raw text and can rescore purely
 * lexical candidates. Concrete runtimes are adapters outside Pinakes.
 */
public interface Reranker {

    /**
     * Score the candidates of the request.
     *
     * <p>Return at most one result per candidate, in any order. A candidate the reranker
     * leaves out is treated as irrelevant and dropped from the reranked result. Results for
     * keys that are not part of the request make the whole answer invalid.
     *
     * @throws RerankException if the runtime fails or is unavailable
     */
    List<RerankedResult> rerank(RerankRequest request) throws RerankException;
}
