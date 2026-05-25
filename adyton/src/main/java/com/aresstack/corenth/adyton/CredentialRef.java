package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * A reference to a credential identity within the vault boundary.
 * <p>
 * Combines a principal (user/service identity) with a {@link SecretRef} that
 * points to the associated secret material. The credential value is never exposed
 * through this object.
 * <p>
 * <b>Migration note:</b> Adapts {@code core/.../files/auth/Credentials.java}
 * which directly exposes {@code getUsername()} and {@code getPassword()}.
 * In Corenth, the password is replaced by an opaque {@link SecretRef} and only
 * the principal identity is readable.
 *
 * @see SecretRef
 */
public final class CredentialRef {

    private final String principal;
    private final SecretRef secretRef;

    public CredentialRef(String principal, SecretRef secretRef) {
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        if (secretRef == null) {
            throw new IllegalArgumentException("SecretRef must not be null");
        }
        this.principal = principal;
        this.secretRef = secretRef;
    }

    /** Returns the principal (identity) associated with this credential. */
    public String principal() {
        return principal;
    }

    /** Returns the opaque reference to the secret material. */
    public SecretRef secretRef() {
        return secretRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CredentialRef)) return false;
        CredentialRef that = (CredentialRef) o;
        return principal.equals(that.principal) && secretRef.equals(that.secretRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principal, secretRef);
    }

    @Override
    public String toString() {
        return "CredentialRef{principal='" + principal + "'}";
    }
}
