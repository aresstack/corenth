package com.aresstack.corenth.adyton;

/**
 * Discriminator for authentication methods supported by the access broker.
 * <p>
 * Each value represents a distinct mechanism by which secrets are turned into
 * authenticated access. Connectors specify the method in their
 * {@link AccessRequest}; the broker selects the matching
 * {@link AuthenticationStrategy}.
 * <p>
 * <b>Migration note:</b> In MainframeMate, the authentication method is
 * implicit — the FTP connector always calls {@code CredentialStore.resolve()}
 * for a password; the Wiki connector always calls the MediaWiki login API.
 * In Corenth, the method is explicit, allowing the broker to validate and
 * route appropriately.
 * <p>
 * This enum is intentionally extensible through future values. Initial values
 * represent the methods discovered in the MainframeMate research code.
 */
public enum AuthenticationMethod {

    /** FTP password authentication (USER/PASS). */
    FTP_PASSWORD,

    /** NDV/ESSO password authentication. */
    NDV_PASSWORD,

    /** MediaWiki login API (returns session cookie). */
    MEDIA_WIKI_LOGIN,

    /** HTTP Basic Authentication (base64 header). */
    HTTP_BASIC,

    /** Mutual TLS using a Windows certificate store. */
    MTLS_CERTIFICATE,

    /** SMB/CIFS network authentication (net use). */
    SMB_NET_USE,

    /** Single sign-on (OS-level, no explicit credentials). */
    SSO
}
