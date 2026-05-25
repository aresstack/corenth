package com.aresstack.corenth.adyton;

import java.util.Arrays;

/**
 * Package-private implementation of {@link SecretMaterial}.
 * <p>
 * Only the broker and cache create instances of this class. External strategy
 * implementations receive it through the {@link SecretMaterial} interface
 * without access to the constructor.
 */
final class DefaultSecretMaterial implements SecretMaterial {

    private final String secretRefId;
    private final String principal;
    private final char[] secret;

    /**
     * Creates a secret material instance.
     *
     * @param secretRefId the internal secret reference identifier
     * @param principal   the principal/username
     * @param secret      the secret value (password, token, key material)
     */
    DefaultSecretMaterial(String secretRefId, String principal, char[] secret) {
        if (secretRefId == null || secretRefId.isEmpty()) {
            throw new IllegalArgumentException("Secret ref id must not be null or empty");
        }
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        if (secret == null) {
            throw new IllegalArgumentException("Secret must not be null");
        }
        this.secretRefId = secretRefId;
        this.principal = principal;
        this.secret = Arrays.copyOf(secret, secret.length);
    }

    /**
     * Convenience constructor for simple cases (e.g., tests, adapters that
     * only need a reference id).
     */
    DefaultSecretMaterial(String secretRefId) {
        this(secretRefId, secretRefId, new char[0]);
    }

    @Override
    public String secretRefId() {
        return secretRefId;
    }

    @Override
    public String principal() {
        return principal;
    }

    @Override
    public char[] secret() {
        return Arrays.copyOf(secret, secret.length);
    }

    @Override
    public String toString() {
        return "SecretMaterial{***}";
    }
}
