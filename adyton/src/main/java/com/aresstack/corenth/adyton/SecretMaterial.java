package com.aresstack.corenth.adyton;

/**
 * Vault-internal boundary type representing resolved secret material.
 * <p>
 * <b>This class is strictly internal to the vault boundary.</b> It must never
 * appear on any module-facing API, connector interface, or public getter.
 * Only trusted {@link AuthenticationStrategy} implementations receive this
 * type, and only during the authentication step.
 * <p>
 * The class is deliberately opaque. It does not have a public
 * {@code getPassword()} or {@code toCharArray()} method. Strategy
 * implementations access the material through package-private methods that
 * are not visible to code outside the {@code adyton} package.
 * <p>
 * <b>Migration note:</b> In MainframeMate, {@code Credentials.getPassword()}
 * returns a raw {@code String}. In Corenth, raw secret material is encapsulated
 * here and never crosses the vault boundary. The closest MainframeMate
 * equivalent is the decrypted output of {@code SessionCipher.decrypt()}, which
 * also should not have been public — adyton formalizes that constraint.
 * <p>
 * <b>Security properties:</b>
 * <ul>
 *   <li>{@code toString()} never reveals the material</li>
 *   <li>No public getter for the raw value</li>
 *   <li>Implementations should support explicit wiping when possible</li>
 * </ul>
 *
 * @see AuthenticationStrategy#authenticate(AccessRequest, SecretMaterial)
 * @see SecretMaterialCache
 */
public final class SecretMaterial {

    private final String secretRefId;

    /**
     * Creates a secret material instance.
     * <p>
     * Package-private: only the broker and cache create these.
     *
     * @param secretRefId the internal secret reference identifier
     */
    SecretMaterial(String secretRefId) {
        if (secretRefId == null || secretRefId.isEmpty()) {
            throw new IllegalArgumentException("Secret ref id must not be null or empty");
        }
        this.secretRefId = secretRefId;
    }

    /**
     * Returns the internal identifier for locating the secret.
     * <p>
     * Package-private: only strategies within adyton can access this.
     */
    String secretRefId() {
        return secretRefId;
    }

    @Override
    public String toString() {
        return "SecretMaterial{***}";
    }
}
