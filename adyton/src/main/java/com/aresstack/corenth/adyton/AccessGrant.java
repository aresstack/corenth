package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * A scoped, time-limited access grant issued by the broker.
 * <p>
 * This is the metadata that connectors can inspect to understand what access
 * they have been granted. It does not contain secret material — only the
 * scope, target, lifetime and an opaque grant id.
 * <p>
 * Normal modules receive an {@code AccessGrant} through {@link AccessHandle#grant()}.
 * <p>
 * <b>Relationship to CredentialLease:</b> {@link CredentialLease} is the
 * vault-internal lease used between the broker and the credential provider SPI.
 * {@code AccessGrant} is the connector-facing view of the same concept —
 * it carries the same scoping but is tied to the protocol-specific handle.
 *
 * @see AccessBroker
 * @see AccessHandle
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
