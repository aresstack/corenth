package com.aresstack.corenth.adyton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default access broker backed by a {@link SecretMaterialProvider}.
 * <p>
 * This implementation keeps the connector-facing contract narrow: connectors
 * provide an {@link AccessRequest}, a protocol-specific {@link AuthenticationStrategy}
 * and receive only an {@link AccessHandle}. Secret material is resolved, cached,
 * released and wiped inside adyton.
 */
public final class ProviderBackedAccessBroker implements AccessBroker, AutoCloseable {

    private final SecretMaterialProvider materialProvider;
    private final SecretMaterialCache materialCache;
    private final Map<String, AccessHandle> activeHandles = new ConcurrentHashMap<>();

    /**
     * Creates a broker with the default RAM-only secret cache policy.
     *
     * @param materialProvider the trusted secret material provider
     */
    public ProviderBackedAccessBroker(SecretMaterialProvider materialProvider) {
        this(materialProvider, SecretCachePolicy.defaultPolicy());
    }

    /**
     * Creates a broker with an explicit cache policy.
     *
     * @param materialProvider the trusted secret material provider
     * @param cachePolicy      the secret material cache policy
     */
    public ProviderBackedAccessBroker(SecretMaterialProvider materialProvider,
                                      SecretCachePolicy cachePolicy) {
        if (materialProvider == null) {
            throw new IllegalArgumentException("Material provider must not be null");
        }
        if (cachePolicy == null) {
            throw new IllegalArgumentException("Cache policy must not be null");
        }
        this.materialProvider = materialProvider;
        this.materialCache = new SecretMaterialCache(cachePolicy);
    }

    @Override
    public <H extends AccessHandle, R> R withAccess(AccessRequest request,
                                                    AuthenticationStrategy<H> strategy,
                                                    AccessOperation<H, R> operation)
            throws AccessException, AuthCancelledException {
        if (operation == null) {
            throw new IllegalArgumentException("Operation must not be null");
        }

        H handle = acquire(request, strategy);
        try {
            return operation.execute(handle);
        } catch (AccessException e) {
            throw e;
        } catch (Exception e) {
            throw new AccessException("Access operation failed", e);
        } finally {
            closeHandle(handle);
        }
    }

    @Override
    public <H extends AccessHandle> H acquire(AccessRequest request,
                                              AuthenticationStrategy<H> strategy)
            throws AccessException, AuthCancelledException {
        validateRequest(request, strategy);

        SecretMaterial material = resolveMaterial(request);
        boolean releaseAfterAuthentication = !materialCache.policy().isEnabled();
        try {
            H handle = strategy.authenticate(request, material);
            if (handle == null) {
                throw new AccessException("Authentication strategy returned no access handle");
            }
            activeHandles.put(handle.grant().grantId(), handle);
            return handle;
        } finally {
            if (releaseAfterAuthentication) {
                materialProvider.release(material);
            }
        }
    }

    @Override
    public void revoke(AccessGrant grant) {
        if (grant == null) {
            return;
        }
        AccessHandle handle = activeHandles.remove(grant.grantId());
        if (handle != null) {
            handle.close();
        }
        materialCache.revokeAll(grant.targetSystem());
    }

    @Override
    public void close() {
        for (AccessHandle handle : activeHandles.values()) {
            handle.close();
        }
        activeHandles.clear();
        materialCache.close();
    }

    /** Returns the number of active handles. Intended for tests and diagnostics. */
    int activeHandleCount() {
        return activeHandles.size();
    }

    private <H extends AccessHandle> void validateRequest(AccessRequest request,
                                                          AuthenticationStrategy<H> strategy)
            throws AccessException {
        if (request == null) {
            throw new IllegalArgumentException("Access request must not be null");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("Authentication strategy must not be null");
        }
        if (!strategy.supports(request.method())) {
            throw new AccessException("Authentication strategy does not support method: " + request.method());
        }
    }

    private SecretMaterial resolveMaterial(AccessRequest request) throws SecretUnavailableException {
        SecretCacheKey key = SecretCacheKey.from(request);
        SecretMaterial cached = materialCache.get(key);
        if (cached != null) {
            return cached;
        }

        SecretMaterial resolved = materialProvider.resolve(request);
        if (resolved == null) {
            throw new SecretUnavailableException("Secret material is unavailable");
        }
        if (materialCache.policy().isEnabled()) {
            materialCache.put(key, resolved);
        }
        return resolved;
    }

    private void closeHandle(AccessHandle handle) {
        if (handle == null) {
            return;
        }
        activeHandles.remove(handle.grant().grantId());
        handle.close();
    }
}
