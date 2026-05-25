package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * A scoped, time-limited access grant issued by the broker.
 * <p>
 * This is the connector-facing metadata available through a protocol-specific
 * {@link AccessHandle}. It describes what access has been granted without
 * containing secret material — only target, principal, purpose, scope,
 * lifetime, and an opaque grant id.
 * <p>
 * <b>Relationship to CredentialLease:</b> {@link CredentialLease} is the
 * module-facing scoped lease returned by {@link DelegatedAccessProvider}.
 * {@code AccessGrant} is the connector-facing view tied to an authenticated
 * protocol handle returned by {@link AccessBroker}.
 *
 * @see AccessBroker
 * @see AccessHandle
 * @see CredentialLease
 */
public final class AccessGrant {

    private final String grantId;
    private final String targetSystem;
    private final String principal;
    private final String purpose;
    private final String scope;
    private final long expiresAtEpochMillis;

    public AccessGrant(String grantId, String targetSystem, String principal,
                       String purpose, String scope, long expiresAtEpochMillis) {
        if (grantId == null || grantId.isEmpty()) {
            throw new IllegalArgumentException("Grant id must not be null or empty");
        }
        if (targetSystem == null || targetSystem.isEmpty()) {
            throw new IllegalArgumentException("Target system must not be null or empty");
        }
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        this.grantId = grantId;
        this.targetSystem = targetSystem;
        this.principal = principal;
        this.purpose = purpose;
        this.scope = scope;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
    }

    /** Returns the opaque grant identifier (never reveals secret material). */
    public String grantId() {
        return grantId;
    }

    /** Returns the target system this grant provides access to. */
    public String targetSystem() {
        return targetSystem;
    }

    /** Returns the principal/subject this grant is bound to. */
    public String principal() {
        return principal;
    }

    /** Returns the purpose of this grant, may be {@code null}. */
    public String purpose() {
        return purpose;
    }

    /** Returns the allowed operation/scope, may be {@code null}. */
    public String scope() {
        return scope;
    }

    /** Returns the epoch millis after which this grant is no longer valid. */
    public long expiresAtEpochMillis() {
        return expiresAtEpochMillis;
    }

    /** Returns {@code true} if this grant has expired. */
    public boolean isExpired(long currentEpochMillis) {
        return currentEpochMillis >= expiresAtEpochMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessGrant)) return false;
        AccessGrant that = (AccessGrant) o;
        return expiresAtEpochMillis == that.expiresAtEpochMillis
                && grantId.equals(that.grantId)
                && targetSystem.equals(that.targetSystem)
                && principal.equals(that.principal)
                && Objects.equals(purpose, that.purpose)
                && Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grantId, targetSystem, principal, purpose, scope, expiresAtEpochMillis);
    }

    @Override
    public String toString() {
        return "AccessGrant{target='" + targetSystem + "', principal='" + principal
                + "', expiresAt=" + expiresAtEpochMillis + "}";
    }
}
