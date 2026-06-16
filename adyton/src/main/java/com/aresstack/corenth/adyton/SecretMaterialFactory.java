package com.aresstack.corenth.adyton;

/**
 * Factory for creating vault-internal {@link SecretMaterial} instances.
 * <p>
 * This is intentionally the only public construction point for secret material.
 * Trusted adapter modules such as KeePassRPC may create material for the broker,
 * while normal modules still only pass opaque {@link SecretRef} values.
 */
public final class SecretMaterialFactory {

    private SecretMaterialFactory() {
        // Prevent instantiation.
    }

    /**
     * Creates secret material from an explicit reference, principal and secret value.
     *
     * @param secretRef the opaque secret reference
     * @param principal the principal associated with the secret
     * @param secret    the secret value
     * @return secret material owned by the vault boundary
     */
    public static SecretMaterial fromSecret(SecretRef secretRef, String principal, char[] secret) {
        if (secretRef == null) {
            throw new IllegalArgumentException("Secret reference must not be null");
        }
        return new DefaultSecretMaterial(secretRef.id(), principal, secret);
    }

    /**
     * Creates marker material without a secret value.
     * <p>
     * Useful for SSO-style authentication where the strategy needs a scoped
     * material object but no password is resolved.
     *
     * @param secretRef the opaque secret reference
     * @param principal the principal associated with the material
     * @return secret material owned by the vault boundary
     */
    public static SecretMaterial empty(SecretRef secretRef, String principal) {
        return fromSecret(secretRef, principal, new char[0]);
    }
}
