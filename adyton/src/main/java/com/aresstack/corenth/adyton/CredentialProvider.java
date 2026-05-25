package com.aresstack.corenth.adyton;

/**
 * Port for credential provisioning within the vault boundary.
 * <p>
 * Implementations resolve a {@link CredentialRequest} into a time-limited
 * {@link CredentialLease}. Adapters may back this port with KeePass, OS credential
 * stores, environment variables, or other secret backends.
 * <p>
 * Implementations must never log, serialize, or expose raw secret material.
 */
public interface CredentialProvider {

    /**
     * Resolves the given request into a credential lease.
     *
     * @param request the credential request describing what is needed
     * @return a lease granting controlled access to the credential
     * @throws SecretUnavailableException if the credential cannot be provided
     */
    CredentialLease acquire(CredentialRequest request) throws SecretUnavailableException;

    /**
     * Revokes a previously acquired lease, releasing any associated resources.
     *
     * @param lease the lease to revoke
     */
    void release(CredentialLease lease);
}
