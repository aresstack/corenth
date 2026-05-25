package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * An opaque, non-revealing reference to a secret stored within the vault boundary.
 * <p>
 * <b>Important:</b> This class is for vault internals and trusted credential
 * adapters only. Normal modules ({@code holkas}, {@code deigma}, {@code tamias},
 * {@code acropolis}, UI code) should not handle {@code SecretRef} instances.
 * They should use {@link DelegatedAccessProvider} to request scoped access grants
 * and receive {@link CredentialLease} objects instead.
 * <p>
 * A {@code SecretRef} never exposes the secret value itself. It acts as a handle
 * that adapter implementations within the vault can use to locate secret material
 * without passing plaintext through the API boundary.
 * <p>
 * <b>Migration note:</b> In MainframeMate, raw password strings flow through
 * {@code CredentialStore.resolve()} and the {@code Credentials} value object
 * (see {@code core/.../files/auth/Credentials.java}). This class replaces that
 * pattern with an opaque reference — the secret never leaves the vault boundary.
 *
 * @see CredentialRef
 */
public final class SecretRef {

    private final String id;

    public SecretRef(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Secret reference id must not be null or empty");
        }
        this.id = id;
    }

    /** Returns the opaque identifier for this secret reference. */
    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecretRef)) return false;
        SecretRef secretRef = (SecretRef) o;
        return id.equals(secretRef.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SecretRef{id=***}";
    }
}
