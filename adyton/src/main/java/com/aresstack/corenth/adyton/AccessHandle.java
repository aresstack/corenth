package com.aresstack.corenth.adyton;

/**
 * A protocol-specific access handle obtained through the broker.
 * <p>
 * An {@code AccessHandle} represents an authenticated session or capability
 * that connectors use to perform operations. Handles may be long-lived
 * (FTP session, Wiki login cookie) or transient (single-use auth header).
 * <p>
 * Handles are obtained through {@link AccessBroker#withAccess} or
 * {@link AccessBroker#acquire}. When obtained via {@code withAccess}, the
 * broker manages the handle's lifecycle automatically. When obtained via
 * {@code acquire}, the caller must eventually close it.
 * <p>
 * Implementations must not expose raw secret material. The handle represents
 * the *result* of authentication, not the credentials themselves.
 * <p>
 * <b>Three handle patterns</b> (from the MainframeMate analysis):
 * <ol>
 *   <li><b>Long-lived session</b> — FTP client, NDV connection, Wiki cookie.
 *       Reused across many operations. Closed on revoke or timeout.</li>
 *   <li><b>Connectionless/header</b> — Confluence Basic Auth, HTTP Bearer.
 *       Effectively a function {@code HttpRequest → HttpRequest}.</li>
 *   <li><b>Factory-shaped</b> — mTLS {@code SSLContext}. Reused to create
 *       many connections.</li>
 * </ol>
 *
 * @see AccessBroker
 * @see AuthenticationStrategy
 */
public interface AccessHandle extends AutoCloseable {

    /**
     * Returns the access grant associated with this handle.
     * <p>
     * The grant describes the scope, target and lifetime of the access
     * without revealing secret material.
     */
    AccessGrant grant();

    /**
     * Releases resources associated with this handle.
     * <p>
     * After closing, the handle must not be used for further operations.
     * Implementations should wipe any transient secret material.
     */
    @Override
    void close();
}
