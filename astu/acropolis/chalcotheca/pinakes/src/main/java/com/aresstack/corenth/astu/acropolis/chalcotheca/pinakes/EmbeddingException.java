package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Signal that an embedding adapter could not produce vectors.
 */
public class EmbeddingException extends Exception {

    private static final long serialVersionUID = 1L;

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
