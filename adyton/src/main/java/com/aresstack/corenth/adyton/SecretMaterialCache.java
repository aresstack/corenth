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
 *   <li>Keyed by a normalized form of the access request identity
 *       (target + principal + purpose + scope + method).</li>
 *   <li>Entries with different purpose or scope must not share.</li>
 *   <li>Cleared on JVM shutdown and explicit {@link #close()}.</li>
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
 * @see SecretMaterial
 */
public final class SecretMaterialCache implements AutoCloseable {

    private final SecretCachePolicy policy;
    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public SecretMaterialCache(SecretCachePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Cache policy must not be null");
        }
        this.policy = policy;
    }

    /**
     * Stores material in the cache if caching is enabled.
     *
     * @param key      normalized cache key
     * @param material the secret material to cache
     */
    void put(String key, SecretMaterial material) {
        if (!policy.isEnabled()) {
            return;
        }
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Cache key must not be null or empty");
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
     * @param key the cache key
     * @return the cached material, or {@code null} if absent, expired, or idle-evicted
     */
    SecretMaterial get(String key) {
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
            return null;
        }
        if (policy.idleTimeoutMillis() > 0
                && (now - entry.lastAccessedAt) >= policy.idleTimeoutMillis()) {
            entries.remove(key);
            return null;
        }
        entry.lastAccessedAt = now;
        return entry.material;
    }

    /** Removes a specific entry. */
    void remove(String key) {
        entries.remove(key);
    }

    /** Removes all entries matching the given target system. */
    void revokeAll(String targetSystem) {
        entries.entrySet().removeIf(e -> e.getKey().startsWith(targetSystem + ":"));
    }

    /** Returns the current cache size. */
    int size() {
        return entries.size();
    }

    /** Clears all cached material. Called on shutdown and explicit close. */
    @Override
    public void close() {
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
