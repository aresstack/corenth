package com.aresstack.corenth.adyton;

/**
 * Trusted adapter SPI for resolving {@link SecretRef} values into secret material.
 * <p>
 * Implementations live inside the vault boundary and may delegate to KeePassRPC,
 * DPAPI, an operating-system credential store or another secret backend. Normal
 * Corenth modules must not use this SPI directly; they request authenticated
 * access through {@link AccessBroker}.
 */
public interface SecretMaterialProvider {

    /**
     * Resolves secret material for the given access request.
     *
     * @param request the scoped access request
     * @return resolved secret material, owned by the broker after return
     * @throws SecretUnavailableException if material cannot be resolved
     */
    SecretMaterial resolve(AccessRequest request) throws SecretUnavailableException;

    /**
     * Releases material that the broker decided not to keep in its cache.
     * <p>
     * Most providers can implement this as {@code material.close()}. The method is
     * explicit so backends with leases or handles can release those as well.
     *
     * @param material the material to release; may be {@code null}
     */
    void release(SecretMaterial material);
}
