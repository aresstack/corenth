package com.aresstack.corenth.adyton;

/**
 * Thrown when a credential request is explicitly cancelled by the user or caller.
 * <p>
 * This is a subtype of {@link SecretUnavailableException} — callers that only
 * catch the parent type will handle cancellation uniformly. Callers that need
 * to distinguish user-initiated cancellation (e.g., to avoid retry loops) can
 * catch this type specifically.
 * <p>
 * <b>Migration note:</b> Adapts {@code core/.../files/auth/AuthCancelledException}
 * which was a {@code RuntimeException} with a German message. In Corenth, it is
 * a checked exception (subclass of {@link SecretUnavailableException}) forcing
 * callers to handle the failure explicitly.
 */
public class AuthCancelledException extends SecretUnavailableException {

    public AuthCancelledException() {
        super("Credential request cancelled");
    }

    public AuthCancelledException(String message) {
        super(message);
    }
}
