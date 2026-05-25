package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * A time-limited, revocable lease granting access to a credential.
 * <p>
 * A lease is the result of a successful {@link CredentialRequest}. It provides
 * a {@link CredentialRef} and carries an expiration epoch after which the lease
 * is no longer valid. Callers should not cache or persist leases beyond their
 * stated lifetime.
 * <p>
 * The lease intentionally does not expose the secret value itself. Consumers
 * use the lease to perform delegated operations through the vault boundary.
 */
public final class CredentialLease {

    private final CredentialRef credentialRef;
    private final long expiresAtEpochMillis;

    public CredentialLease(CredentialRef credentialRef, long expiresAtEpochMillis) {
        if (credentialRef == null) {
            throw new IllegalArgumentException("CredentialRef must not be null");
        }
        this.credentialRef = credentialRef;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
    }

    /** Returns the credential reference associated with this lease. */
    public CredentialRef credentialRef() {
        return credentialRef;
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
                && credentialRef.equals(that.credentialRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentialRef, expiresAtEpochMillis);
    }

    @Override
    public String toString() {
        return "CredentialLease{credentialRef=" + credentialRef + ", expiresAt=" + expiresAtEpochMillis + "}";
    }
}
