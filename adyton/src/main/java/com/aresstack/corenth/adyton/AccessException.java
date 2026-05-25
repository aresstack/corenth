package com.aresstack.corenth.adyton;

/**
 * Thrown when an access operation through the broker cannot be completed.
 * <p>
 * This is the primary checked exception for the {@link AccessBroker} API.
 * Subtypes distinguish specific failure modes:
 * <ul>
 *   <li>{@link SecretUnavailableException} — secret material cannot be resolved</li>
 *   <li>{@link AuthCancelledException} — user-initiated cancellation</li>
 * </ul>
 * <p>
 * <b>Migration note:</b> In MainframeMate, credential failures surface as a
 * mix of unchecked exceptions ({@code AuthCancelledException},
 * {@code KeePassNotAvailableException}). In Corenth, all access failures are
 * checked, forcing callers to handle them explicitly.
 */
public class AccessException extends Exception {

    public AccessException(String message) {
        super(message);
    }

    public AccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
