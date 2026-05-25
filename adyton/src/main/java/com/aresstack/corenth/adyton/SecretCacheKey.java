package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * Typed, composite key for entries in the {@link SecretMaterialCache}.
 * <p>
 * A cache key unambiguously identifies a cached secret entry by combining all
 * relevant request dimensions: credential reference, target system, principal,
 * purpose, scope, and authentication method.
 * <p>
 * This replaces the previous untyped {@code String} key which was brittle because
 * targets themselves contain colons and other delimiters (e.g., {@code ftp:mainframe},
 * {@code https://wiki.example.internal}, {@code keepass://wiki/internal}).
 *
 * @see SecretMaterialCache
 */
public final class SecretCacheKey {

    private final SecretRef credentialRef;
    private final String targetSystem;
    private final String principal;
    private final String purpose;
    private final String scope;
    private final AuthenticationMethod method;

    /**
     * Creates a cache key from all identifying dimensions.
     *
     * @param credentialRef the credential reference (required)
     * @param targetSystem  the target system (required)
     * @param principal     the principal (required)
     * @param purpose       the stated purpose (may be {@code null})
     * @param scope         the requested operation/scope (may be {@code null})
     * @param method        the authentication method (required)
     */
    public SecretCacheKey(SecretRef credentialRef, String targetSystem,
                          String principal, String purpose, String scope,
                          AuthenticationMethod method) {
        if (credentialRef == null) {
            throw new IllegalArgumentException("Credential reference must not be null");
        }
        if (targetSystem == null || targetSystem.isEmpty()) {
            throw new IllegalArgumentException("Target system must not be null or empty");
        }
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        if (method == null) {
            throw new IllegalArgumentException("Authentication method must not be null");
        }
        this.credentialRef = credentialRef;
        this.targetSystem = targetSystem;
        this.principal = principal;
        this.purpose = purpose;
        this.scope = scope;
        this.method = method;
    }

    /**
     * Creates a cache key from an {@link AccessRequest}.
     *
     * @param request the access request to derive the key from
     * @return a cache key matching the request dimensions
     */
    public static SecretCacheKey from(AccessRequest request) {
        return new SecretCacheKey(
                request.credentialRef(),
                request.targetSystem(),
                request.principal(),
                request.purpose(),
                request.scope(),
                request.method());
    }

    /** Returns the credential reference. */
    public SecretRef credentialRef() {
        return credentialRef;
    }

    /** Returns the target system. */
    public String targetSystem() {
        return targetSystem;
    }

    /** Returns the principal. */
    public String principal() {
        return principal;
    }

    /** Returns the purpose, may be {@code null}. */
    public String purpose() {
        return purpose;
    }

    /** Returns the requested operation/scope, may be {@code null}. */
    public String scope() {
        return scope;
    }

    /** Returns the authentication method. */
    public AuthenticationMethod method() {
        return method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecretCacheKey)) return false;
        SecretCacheKey that = (SecretCacheKey) o;
        return credentialRef.equals(that.credentialRef)
                && targetSystem.equals(that.targetSystem)
                && principal.equals(that.principal)
                && Objects.equals(purpose, that.purpose)
                && Objects.equals(scope, that.scope)
                && method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentialRef, targetSystem, principal, purpose, scope, method);
    }

    @Override
    public String toString() {
        return "SecretCacheKey{target='" + targetSystem + "', principal='" + principal
                + "', method=" + method + "}";
    }
}
