package com.aresstack.corenth.adyton;

/**
 * Thrown when the owner of a secret explicitly refuses to release it for the stated request.
 * <p>
 * Like {@link AuthCancelledException}, this is a subtype of {@link SecretUnavailableException},
 * so callers that only catch the parent type keep handling it uniformly. Callers that chain
 * secret sources must stop on this type instead of falling through to the next source: a refusal
 * is a decision, not a missing secret. Callers that report outcomes can tell a refusal apart from
 * a cancelled interaction and from an unavailable backend.
 */
public class SecretReleaseDeniedException extends SecretUnavailableException {

    public SecretReleaseDeniedException() {
        super("Secret release denied");
    }

    public SecretReleaseDeniedException(String message) {
        super(message);
    }
}
