package com.aresstack.corenth.adyton;

/**
 * The connector-facing API for obtaining authenticated access.
 * <p>
 * This is the primary interface that connectors ({@code holkas} FTP,
 * {@code deigma} NDV, {@code tamias} Wiki/Confluence, etc.) use to obtain
 * authenticated access without directly resolving passwords.
 * <p>
 * The broker owns the full lifecycle:
 * <ol>
 *   <li>Validate the request.</li>
 *   <li>Resolve or load secret material inside adyton (from cache or provider).</li>
 *   <li>Use RAM cache if policy allows.</li>
 *   <li>Call the selected {@link AuthenticationStrategy}.</li>
 *   <li>Execute the operation with the {@link AccessHandle}.</li>
 *   <li>Close/revoke the access handle.</li>
 *   <li>Wipe temporary {@link SecretMaterial} where possible.</li>
 * </ol>
 * <p>
 * <b>Two entry points:</b>
 * <ul>
 *   <li>{@link #withAccess} — safe default. The broker opens and closes the
 *       handle deterministically around the operation callback.</li>
 *   <li>{@link #acquire} — for long-lived handles (Wiki search-as-you-type,
 *       NDV repeated PAL calls, FTP multi-file transfers). The caller is
 *       responsible for closing the handle.</li>
 * </ul>
 * <p>
 * <b>Migration note:</b> In MainframeMate, connectors directly call
 * {@code CredentialStore.resolve()} or {@code getCredentials()},
 * receiving raw username/password. In Corenth, connectors go through the
 * broker — they never see raw secret material, only protocol-specific handles.
 * <p>
 * The analysis document
 * ({@code docs/analysis/mainframemate-authentication-flows.md}) validates
 * this model against all MainframeMate authentication flows.
 *
 * @see AccessRequest
 * @see AccessHandle
 * @see AuthenticationStrategy
 * @see AccessOperation
 */
public interface AccessBroker {

    /**
     * Executes an operation within the lifecycle of an authenticated access handle.
     * <p>
     * This is the safe default. The broker:
     * <ol>
     *   <li>Resolves secret material (from cache or provider).</li>
     *   <li>Calls the strategy to produce a handle.</li>
     *   <li>Passes the handle to the operation.</li>
     *   <li>Closes the handle after the operation completes (or fails).</li>
     * </ol>
     *
     * @param request   the scoped access request
     * @param strategy  the authentication strategy for the target protocol
     * @param operation the operation to execute with the handle
     * @param <H>       the handle type
     * @param <R>       the operation result type
     * @return the operation result
     * @throws AccessException if authentication or the operation fails
     * @throws AuthCancelledException if the user cancelled the credential request
     */
    <H extends AccessHandle, R> R withAccess(
            AccessRequest request,
            AuthenticationStrategy<H> strategy,
            AccessOperation<H, R> operation)
            throws AccessException, AuthCancelledException;

    /**
     * Acquires a long-lived access handle for reuse across multiple operations.
     * <p>
     * Required because Wiki, NDV and FTP benefit from pre-acquired sessions
     * (search-as-you-type, repeated PAL calls, multi-file transfers).
     * <p>
     * The caller is responsible for closing the returned handle when done.
     * The broker may still revoke the handle if it expires or if
     * {@link #revoke(AccessGrant)} is called.
     *
     * @param request  the scoped access request
     * @param strategy the authentication strategy for the target protocol
     * @param <H>      the handle type
     * @return an authenticated access handle
     * @throws AccessException if authentication fails
     * @throws AuthCancelledException if the user cancelled the credential request
     */
    <H extends AccessHandle> H acquire(
            AccessRequest request,
            AuthenticationStrategy<H> strategy)
            throws AccessException, AuthCancelledException;

    /**
     * Revokes an active access grant, releasing all associated resources.
     * <p>
     * After revocation, any handle associated with this grant must no longer
     * be used.
     *
     * @param grant the grant to revoke
     */
    void revoke(AccessGrant grant);
}
