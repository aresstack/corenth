package com.aresstack.corenth.adyton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vault-internal cache for resolved secret material.
 * <p>
 * <b>This class is strictly internal to the vault boundary.</b> It must not
 * be exposed to normal modules or connectors. Only the {@link AccessBroker}
 * implementation and trusted adapters interact with this cache.
 * <p>
 * <b>Cache semantics:</b>
 * <ul>
 *   <li>RAM-only — never persisted to disk.</li>
 *   <li>Configurable via {@link SecretCachePolicy} (TTL, idle timeout, enabled).</li>
 *   <li>Keyed by a typed {@link SecretCacheKey} combining credential reference,
 *       target, principal, purpose, and authentication method.</li>
 *   <li>Entries with different purpose or scope must not share.</li>
 *   <li>The broker or runtime must call {@link #close()} during shutdown;
 *       this class does not register a JVM shutdown hook.</li>
 *   <li>No logging of secret values.</li>
 * </ul>
 * <p>
 * <b>Migration note:</b> Replaces both MainframeMate caches
 * ({@code CredentialStore.sessionCache} and
 * {@code LoginManager.sessionPasswordCache}) with a single, policy-driven
 * cache that enforces TTL and idle timeout — fixing the structural defects
 * where two independent caches with different keys and lifecycles caused bugs.
 *
 * @see SecretCachePolicy
 * @see SecretCacheKey
 * @see SecretMaterial
 */
public final class SecretMaterialCache implements AutoCloseable {

    private final SecretCachePolicy policy;
    private final Map<SecretCacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

    public SecretMaterialCache(SecretCachePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Cache policy must not be null");
        }
        this.policy = policy;
    }

    /**
     * Stores material in the cache if caching is enabled.
     *
     * @param key      typed cache key
     * @param material the secret material to cache
     */
    void put(SecretCacheKey key, SecretMaterial material) {
        if (!policy.isEnabled()) {
            return;
        }
        if (key == null) {
            throw new IllegalArgumentException("Cache key must not be null");
        }
        if (material == null) {
            throw new IllegalArgumentException("Material must not be null");
        }
        long now = System.currentTimeMillis();
        long expiresAt = now + policy.ttlMillis();
        entries.put(key, new CacheEntry(material, expiresAt, now));
    }

    /**
     * Retrieves non-expired material from the cache.
     *
     * @param key the typed cache key
     * @return the cached material, or {@code null} if absent, expired, or idle-evicted
     */
    SecretMaterial get(SecretCacheKey key) {
        if (!policy.isEnabled()) {
            return null;
        }
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now >= entry.expiresAt) {
            entries.remove(key);
            entry.material.close();
            return null;
        }
        if (policy.idleTimeoutMillis() > 0
                && (now - entry.lastAccessedAt) >= policy.idleTimeoutMillis()) {
            entries.remove(key);
            entry.material.close();
            return null;
        }
        entry.lastAccessedAt = now;
        return entry.material;
    }

    /** Removes a specific entry by key, wiping the material. */
    void remove(SecretCacheKey key) {
        CacheEntry removed = entries.remove(key);
        if (removed != null) {
            removed.material.close();
        }
    }

    /** Removes all entries matching the given target system, wiping each. */
    void revokeAll(String targetSystem) {
        entries.entrySet().removeIf(e -> {
            if (e.getKey().targetSystem().equals(targetSystem)) {
                e.getValue().material.close();
                return true;
            }
            return false;
        });
    }

    /** Returns the current cache size. */
    int size() {
        return entries.size();
    }

    /** Clears all cached material, wiping each entry. Called on shutdown and explicit close. */
    @Override
    public void close() {
        for (CacheEntry entry : entries.values()) {
            entry.material.close();
        }
        entries.clear();
    }

    /** Returns the policy governing this cache. */
    public SecretCachePolicy policy() {
        return policy;
    }

    private static final class CacheEntry {
        final SecretMaterial material;
        final long expiresAt;
        volatile long lastAccessedAt;

        CacheEntry(SecretMaterial material, long expiresAt, long lastAccessedAt) {
            this.material = material;
            this.expiresAt = expiresAt;
            this.lastAccessedAt = lastAccessedAt;
        }
    }
}
