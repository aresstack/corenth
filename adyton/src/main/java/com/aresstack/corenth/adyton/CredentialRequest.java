package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * A scoped request for delegated access issued by a module.
 * <p>
 * The request identifies the target system, principal, purpose, requested
 * operation/scope and desired TTL. It does not contain or reveal any secret
 * material. The vault boundary resolves the request into a time-limited
 * {@link CredentialLease} (scoped access grant).
 * <p>
 * <b>Migration note:</b> Adapts {@code core/.../files/auth/ConnectionId.java}
 * (which combined scheme, host, username) into a more general request model.
 * The namespaced component keys used in {@code CredentialStore} (e.g.
 * {@code "ftp:myhost"}, {@code "wiki:wikipedia_de"}) map to the
 * {@code targetSystem} field here. Corenth adds explicit scope, operation, and
 * TTL fields that MainframeMate did not have.
 *
 * @see DelegatedAccessProvider#request(CredentialRequest)
 * @see CredentialProvider#acquire(CredentialRequest)
 */
public final class CredentialRequest {

    private final String targetSystem;
    private final String principal;
    private final String purpose;
    private final String scope;
    private final long requestedTtlMillis;

    /**
     * Creates a request with all fields.
     *
     * @param targetSystem      the target system or resource (required)
     * @param principal         the principal/subject identity (required)
     * @param purpose           the stated purpose of the request (may be {@code null})
     * @param scope             the requested operation or scope (may be {@code null})
     * @param requestedTtlMillis the desired lease lifetime in millis (0 = use default)
     */
    public CredentialRequest(String targetSystem, String principal, String purpose,
                             String scope, long requestedTtlMillis) {
        if (targetSystem == null || targetSystem.isEmpty()) {
            throw new IllegalArgumentException("Target system must not be null or empty");
        }
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        this.targetSystem = targetSystem;
        this.principal = principal;
        this.purpose = purpose;
        this.scope = scope;
        this.requestedTtlMillis = requestedTtlMillis;
    }

    /**
     * Creates a request with target, principal and purpose (scope and TTL default).
     *
     * @param targetSystem the target system or resource (required)
     * @param principal    the principal/subject identity (required)
     * @param purpose      the stated purpose of the request (may be {@code null})
     */
    public CredentialRequest(String targetSystem, String principal, String purpose) {
        this(targetSystem, principal, purpose, null, 0L);
    }

    /** Returns the target system or resource this request is for. */
    public String targetSystem() {
        return targetSystem;
    }

    /** Returns the principal (subject/identity) being requested. */
    public String principal() {
        return principal;
    }

    /** Returns the stated purpose of the request, may be {@code null}. */
    public String purpose() {
        return purpose;
    }

    /** Returns the requested operation or scope, may be {@code null}. */
    public String scope() {
        return scope;
    }

    /** Returns the requested lease TTL in millis (0 means use provider default). */
    public long requestedTtlMillis() {
        return requestedTtlMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CredentialRequest)) return false;
        CredentialRequest that = (CredentialRequest) o;
        return requestedTtlMillis == that.requestedTtlMillis
                && targetSystem.equals(that.targetSystem)
                && principal.equals(that.principal)
                && Objects.equals(purpose, that.purpose)
                && Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetSystem, principal, purpose, scope, requestedTtlMillis);
    }

    @Override
    public String toString() {
        return "CredentialRequest{target='" + targetSystem + "', principal='" + principal + "'}";
    }
}
