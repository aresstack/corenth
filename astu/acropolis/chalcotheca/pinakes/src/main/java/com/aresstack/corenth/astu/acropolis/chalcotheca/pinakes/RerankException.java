package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Signal that a reranker adapter could not score candidates.
 */
public class RerankException extends Exception {

    private static final long serialVersionUID = 1L;

    public RerankException(String message) {
        super(message);
    }

    public RerankException(String message, Throwable cause) {
        super(message, cause);
    }
}
