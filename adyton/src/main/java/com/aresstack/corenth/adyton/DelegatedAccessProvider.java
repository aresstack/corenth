package com.aresstack.corenth.adyton;

/**
 * Module-facing API for delegated access operations within the vault boundary.
 * <p>
 * This is the primary interface that normal Corenth modules ({@code holkas},
 * {@code deigma}, {@code tamias}, {@code acropolis}, UI code) should use.
 * Rather than handling secret references or raw credentials, modules request
 * scoped, short-lived access grants and perform delegated operations through
 * this provider.
 * <p>
 * <b>Relationship to {@link CredentialProvider}:</b>
 * {@link CredentialProvider} is an adapter SPI used inside {@code adyton} by
 * trusted credential backends (KeePass, DPAPI, OS stores). Normal modules
 * do not interact with {@code CredentialProvider} directly — they use this
 * interface instead.
 * <p>
 * Implementations may delegate to KeePassRPC, OS credential managers, or
 * other secret backends that support operation-based access.
 * <p>
 * <b>Migration note:</b> This is a new Corenth API without a direct
 * MainframeMate equivalent. MainframeMate's {@code KeePassRpcClient} performs
 * authenticated operations against KeePass via WebSocket/SRP, which is the
 * closest precedent for delegated secret operations. A future KeePassRPC
 * adapter would implement this port.
 */
public interface DelegatedAccessProvider {

    /**
     * Requests a scoped, time-limited access grant for the given credential request.
     * <p>
     * This is the primary entry point for normal modules. The returned lease
     * represents a delegated capability — modules do not receive raw secrets.
     *
     * @param request the scoped request describing target, principal, purpose and scope
     * @return a time-limited lease granting delegated access
     * @throws SecretUnavailableException if the access cannot be granted
     */
    CredentialLease request(CredentialRequest request) throws SecretUnavailableException;

    /**
     * Performs a delegated authentication operation using an active lease.
     *
     * @param lease  the active lease authorizing the operation
     * @param target the target system or endpoint to authenticate against
     * @return a token or confirmation of the delegated operation
     * @throws SecretUnavailableException if the operation cannot be performed
     *         or the lease has expired
     */
    DelegatedAccessResult authenticate(CredentialLease lease, String target)
            throws SecretUnavailableException;

    /**
     * Revokes an active lease, releasing any associated resources.
     *
     * @param lease the lease to revoke
     */
    void revoke(CredentialLease lease);
}
