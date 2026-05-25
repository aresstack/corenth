package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * An opaque, non-revealing reference to a secret stored within the vault boundary.
 * <p>
 * A {@code SecretRef} never exposes the secret value itself. It acts as a handle
 * that modules outside the vault can pass around to request controlled operations
 * without gaining access to the underlying secret material.
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
