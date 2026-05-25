package com.aresstack.corenth.adyton;

/**
 * Thrown when a requested secret or credential cannot be provided.
 * <p>
 * This exception is deliberately vague about the reason: it does not reveal
 * whether the secret exists but is inaccessible, or does not exist at all.
 * Callers should handle this uniformly as "access denied or unavailable".
 */
public class SecretUnavailableException extends Exception {

    public SecretUnavailableException(String message) {
        super(message);
    }

    public SecretUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
