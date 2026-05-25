package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * A request for credentials issued by a module that needs controlled access.
 * <p>
 * The request identifies the target system and required principal but does not
 * contain or reveal any secret material. The vault boundary resolves the request
 * into a time-limited {@link CredentialLease}.
 * <p>
 * <b>Migration note:</b> Adapts {@code core/.../files/auth/ConnectionId.java}
 * (which combined scheme, host, username) into a more general request model.
 * The namespaced component keys used in {@code CredentialStore} (e.g.
 * {@code "ftp:myhost"}, {@code "wiki:wikipedia_de"}) map to the
 * {@code targetSystem} field here.
 *
 * @see CredentialProvider#acquire(CredentialRequest)
 */
public final class CredentialRequest {

    private final String targetSystem;
    private final String principal;
    private final String purpose;

    public CredentialRequest(String targetSystem, String principal, String purpose) {
        if (targetSystem == null || targetSystem.isEmpty()) {
            throw new IllegalArgumentException("Target system must not be null or empty");
        }
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        this.targetSystem = targetSystem;
        this.principal = principal;
        this.purpose = purpose;
    }

    /** Returns the target system this credential request is for. */
    public String targetSystem() {
        return targetSystem;
    }

    /** Returns the principal (identity) being requested. */
    public String principal() {
        return principal;
    }

    /** Returns the stated purpose of the request, may be {@code null}. */
    public String purpose() {
        return purpose;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CredentialRequest)) return false;
        CredentialRequest that = (CredentialRequest) o;
        return targetSystem.equals(that.targetSystem)
                && principal.equals(that.principal)
                && Objects.equals(purpose, that.purpose);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetSystem, principal, purpose);
    }

    @Override
    public String toString() {
        return "CredentialRequest{target='" + targetSystem + "', principal='" + principal + "'}";
    }
}
