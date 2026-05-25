package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * A scoped, time-limited access grant (lease) for delegated operations.
 * <p>
 * This is the primary access object that normal Corenth modules receive.
 * A lease represents a delegated, short-lived capability bound to a specific
 * target, principal, purpose, and scope. After expiration, the lease is
 * rejected on any access attempt.
 * <p>
 * <b>Security model:</b> Normal modules receive only this grant. They do not
 * handle {@link SecretRef} or raw credential material. The grant carries all
 * information needed to perform delegated operations through
 * {@link DelegatedAccessProvider}.
 * <p>
 * <b>Migration note:</b> In MainframeMate, {@code CredentialStore}'s session
 * cache holds encrypted credentials indefinitely until application exit. The
 * lease model adds explicit expiration and scope binding, preventing long-lived
 * secret handles from accumulating in memory. Corenth intentionally changes the
 * security model:
 * <ul>
 *   <li>MainframeMate: credentials can be resolved as raw username/password.</li>
 *   <li>Corenth: normal modules receive only scoped, short-lived delegated access grants.</li>
 * </ul>
 *
 * @see DelegatedAccessProvider#request(CredentialRequest)
 * @see CredentialProvider#acquire(CredentialRequest)
 */
public final class CredentialLease {

    private final String leaseId;
    private final String targetSystem;
    private final String principal;
    private final String purpose;
    private final String scope;
    private final long expiresAtEpochMillis;

    /**
     * Creates a lease with full grant semantics.
     *
     * @param leaseId              opaque lease identifier
     * @param targetSystem         the target system this lease grants access to
     * @param principal            the principal/subject this lease is bound to
     * @param purpose              the purpose of this grant (may be {@code null})
     * @param scope                the allowed operation/scope (may be {@code null})
     * @param expiresAtEpochMillis epoch millis after which this lease is no longer valid
     */
    public CredentialLease(String leaseId, String targetSystem, String principal,
                           String purpose, String scope, long expiresAtEpochMillis) {
        if (leaseId == null || leaseId.isEmpty()) {
            throw new IllegalArgumentException("Lease id must not be null or empty");
        }
        if (targetSystem == null || targetSystem.isEmpty()) {
            throw new IllegalArgumentException("Target system must not be null or empty");
        }
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        this.leaseId = leaseId;
        this.targetSystem = targetSystem;
        this.principal = principal;
        this.purpose = purpose;
        this.scope = scope;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
    }

    /** Returns the opaque lease identifier (never reveals secret material). */
    public String leaseId() {
        return leaseId;
    }

    /** Returns the target system this lease grants access to. */
    public String targetSystem() {
        return targetSystem;
    }

    /** Returns the principal/subject this lease is bound to. */
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

    /** Returns the epoch millis after which this lease is no longer valid. */
    public long expiresAtEpochMillis() {
        return expiresAtEpochMillis;
    }

    /** Returns {@code true} if this lease has expired relative to the given time. */
    public boolean isExpired(long currentEpochMillis) {
        return currentEpochMillis >= expiresAtEpochMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CredentialLease)) return false;
        CredentialLease that = (CredentialLease) o;
        return expiresAtEpochMillis == that.expiresAtEpochMillis
                && leaseId.equals(that.leaseId)
                && targetSystem.equals(that.targetSystem)
                && principal.equals(that.principal)
                && Objects.equals(purpose, that.purpose)
                && Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leaseId, targetSystem, principal, purpose, scope, expiresAtEpochMillis);
    }

    @Override
    public String toString() {
        return "CredentialLease{target='" + targetSystem + "', principal='" + principal
                + "', expiresAt=" + expiresAtEpochMillis + "}";
    }
}
