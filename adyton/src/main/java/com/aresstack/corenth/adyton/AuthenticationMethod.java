package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * Discriminator for authentication methods supported by the access broker.
 * <p>
 * Each instance represents a distinct mechanism by which secrets are turned
 * into authenticated access. Connectors specify the method in their
 * {@link AccessRequest}; the broker selects the matching
 * {@link AuthenticationStrategy}.
 * <p>
 * This is an <b>open value object</b> — adapter modules can introduce new
 * authentication methods without modifying adyton core, similar to how
 * {@code ResourceScheme} works in PR #17.
 * <p>
 * <b>Migration note:</b> In MainframeMate, the authentication method is
 * implicit — the FTP connector always calls {@code CredentialStore.resolve()}
 * for a password; the Wiki connector always calls the MediaWiki login API.
 * In Corenth, the method is explicit, allowing the broker to validate and
 * route appropriately.
 *
 * @see AccessRequest
 * @see AuthenticationStrategy#supports(AuthenticationMethod)
 */
public final class AuthenticationMethod {

    /** FTP password authentication (USER/PASS). */
    public static final AuthenticationMethod FTP_PASSWORD = of("ftp-password");

    /** NDV/ESSO password authentication. */
    public static final AuthenticationMethod NDV_PASSWORD = of("ndv-password");

    /** MediaWiki login API (returns session cookie). */
    public static final AuthenticationMethod MEDIA_WIKI_LOGIN = of("mediawiki-login");

    /** HTTP Basic Authentication (base64 header). */
    public static final AuthenticationMethod HTTP_BASIC = of("http-basic");

    /** Mutual TLS using a Windows certificate store. */
    public static final AuthenticationMethod MTLS_CERTIFICATE = of("mtls-certificate");

    /** SMB/CIFS network authentication (net use). */
    public static final AuthenticationMethod SMB_NET_USE = of("smb-net-use");

    /** Single sign-on (OS-level, no explicit credentials). */
    public static final AuthenticationMethod SSO = of("sso");

    private final String name;

    private AuthenticationMethod(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Authentication method name must not be null or empty");
        }
        this.name = name;
    }

    /**
     * Creates or retrieves an authentication method by name.
     * <p>
     * Adapter modules can introduce new methods by calling this factory method
     * with a unique name. The built-in constants above cover the methods
     * discovered in the MainframeMate research.
     *
     * @param name the unique method name (e.g., {@code "ftp-password"})
     * @return an {@code AuthenticationMethod} instance
     */
    public static AuthenticationMethod of(String name) {
        return new AuthenticationMethod(name);
    }

    /** Returns the unique name of this authentication method. */
    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthenticationMethod)) return false;
        AuthenticationMethod that = (AuthenticationMethod) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
