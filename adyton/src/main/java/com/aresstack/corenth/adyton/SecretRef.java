package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * An opaque, non-revealing reference to a secret stored within the vault boundary.
 * <p>
 * A {@code SecretRef} is not secret material. It is a stable credential reference
 * that configuration and access requests may carry, for example
 * {@code keepass://wiki/internal}. Normal modules and connector adapters may pass
 * this value to adyton as an opaque identifier, but they must not dereference it,
 * resolve it, or receive the underlying secret material.
 * <p>
 * Trusted credential adapters use this reference inside the vault boundary to
 * locate secret material. The secret itself is represented only as
 * {@link SecretMaterial} during trusted authentication strategy execution.
 * <p>
 * <b>Migration note:</b> In MainframeMate, raw password strings flow through
 * {@code CredentialStore.resolve()} and the {@code Credentials} value object
 * (see {@code core/.../files/auth/Credentials.java}). This class replaces that
 * pattern with an opaque reference — callers can identify which credential is
 * needed without receiving username/password material.
 *
 * @see CredentialRef
 * @see AccessRequest#credentialRef()
 * @see CredentialRequest#credentialRef()
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
