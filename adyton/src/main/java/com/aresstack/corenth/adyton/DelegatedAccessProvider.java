package com.aresstack.corenth.adyton;

/**
 * Port for delegated access operations within the vault boundary.
 * <p>
 * Rather than handing out raw secrets, a delegated access provider performs
 * operations on behalf of the caller using credentials that remain inside the
 * vault. This supports the Corenth direction of controlled access over raw
 * secret exposure.
 * <p>
 * Implementations may delegate to KeePassRPC, OS credential managers, or
 * other secret backends that support operation-based access.
 */
public interface DelegatedAccessProvider {

    /**
     * Performs a delegated authentication operation for the given credential lease.
     *
     * @param lease  the active lease authorizing the operation
     * @param target the target system or endpoint to authenticate against
     * @return a token or confirmation of the delegated operation
     * @throws SecretUnavailableException if the operation cannot be performed
     */
    DelegatedAccessResult authenticate(CredentialLease lease, String target)
            throws SecretUnavailableException;
}
