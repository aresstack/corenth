package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * A scoped request for access issued by a connector or module.
 * <p>
 * Describes what access is needed: target system, principal, purpose,
 * requested operation/scope, desired TTL, and authentication method.
 * <p>
 * The broker uses this to resolve secret material (if cached or from a
 * provider), select the appropriate strategy, and produce an
 * {@link AccessHandle}.
 * <p>
 * <b>Migration note:</b> Extends the {@link CredentialRequest} concept with
 * an explicit {@link AuthenticationMethod} field. In MainframeMate, the
 * authentication method is implicitly determined by the connector type
 * (FTP always uses password, Wiki always uses login API, etc.). In Corenth,
 * the method is an explicit part of the request so the broker can select
 * the correct strategy.
 *
 * @see AccessBroker
 * @see CredentialRequest
 */
public final class AccessRequest {

    private final String targetSystem;
    private final String principal;
    private final String purpose;
    private final String scope;
    private final AuthenticationMethod method;
    private final long requestedTtlMillis;

    /**
     * Creates a fully specified access request.
     *
     * @param targetSystem      the target system or resource (required)
     * @param principal         the principal/subject identity (required)
     * @param purpose           the stated purpose (may be {@code null})
     * @param scope             the requested operation/scope (may be {@code null})
     * @param method            the authentication method to use (required)
     * @param requestedTtlMillis desired TTL in millis (0 = use default)
     */
    public AccessRequest(String targetSystem, String principal, String purpose,
                         String scope, AuthenticationMethod method,
                         long requestedTtlMillis) {
        if (targetSystem == null || targetSystem.isEmpty()) {
            throw new IllegalArgumentException("Target system must not be null or empty");
        }
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        if (method == null) {
            throw new IllegalArgumentException("Authentication method must not be null");
        }
        this.targetSystem = targetSystem;
        this.principal = principal;
        this.purpose = purpose;
        this.scope = scope;
        this.method = method;
        this.requestedTtlMillis = requestedTtlMillis;
    }

    /** Returns the target system or resource. */
    public String targetSystem() {
        return targetSystem;
    }

    /** Returns the principal/subject identity. */
    public String principal() {
        return principal;
    }

    /** Returns the purpose of the request, may be {@code null}. */
    public String purpose() {
        return purpose;
    }

    /** Returns the requested operation/scope, may be {@code null}. */
    public String scope() {
        return scope;
    }

    /** Returns the authentication method to use. */
    public AuthenticationMethod method() {
        return method;
    }

    /** Returns the requested TTL in millis (0 means use provider default). */
    public long requestedTtlMillis() {
        return requestedTtlMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessRequest)) return false;
        AccessRequest that = (AccessRequest) o;
        return requestedTtlMillis == that.requestedTtlMillis
                && targetSystem.equals(that.targetSystem)
                && principal.equals(that.principal)
                && Objects.equals(purpose, that.purpose)
                && Objects.equals(scope, that.scope)
                && method == that.method;
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetSystem, principal, purpose, scope, method, requestedTtlMillis);
    }

    @Override
    public String toString() {
        return "AccessRequest{target='" + targetSystem + "', principal='" + principal
                + "', method=" + method + "}";
    }
}
