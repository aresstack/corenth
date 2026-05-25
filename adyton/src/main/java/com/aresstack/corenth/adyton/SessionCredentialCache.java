package com.aresstack.corenth.adyton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A session-scoped, in-memory cache for active credential leases.
 * <p>
 * <b>Important distinction:</b> This is a <em>lease cache</em>, not the RAM
 * secret-material cache. It holds only opaque {@link CredentialLease} grant
 * objects (target, principal, scope, expiry) — never passwords, tokens, or
 * secret material. The MainframeMate RAM secret-material cache is modeled
 * separately by {@link SecretMaterialCache}.
 * <p>
 * This cache holds leases only for the duration of a session and automatically
 * discards expired entries on access. It never persists secrets to disk and
 * should be discarded when the session ends.
 * <p>
 * <b>Migration note:</b> Adapts the session cache concept from
 * {@code CredentialStore.sessionCache} and {@code SessionCipher}. In
 * MainframeMate, the session cache stores {@code SessionCipher}-encrypted
 * {@code "user|password"} strings in a {@code HashMap}. In Corenth, the cache
 * holds only opaque {@link CredentialLease} references with explicit expiration
 * — no encrypted or plaintext credentials are stored, and expired entries are
 * automatically evicted on access.
 *
 * @see CredentialLease#isExpired(long)
 * @see SecretMaterialCache
 */
public final class SessionCredentialCache {

    private final Map<String, CredentialLease> leases = new ConcurrentHashMap<>();

    /**
     * Stores a lease in the cache, keyed by the credential's principal and target.
     *
     * @param key   the cache key (e.g., "system:principal")
     * @param lease the lease to cache
     */
    public void put(String key, CredentialLease lease) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Cache key must not be null or empty");
        }
        if (lease == null) {
            throw new IllegalArgumentException("Lease must not be null");
        }
        leases.put(key, lease);
    }

    /**
     * Retrieves a non-expired lease from the cache.
     *
     * @param key                the cache key
     * @param currentEpochMillis the current time for expiration check
     * @return the cached lease, or {@code null} if absent or expired
     */
    public CredentialLease get(String key, long currentEpochMillis) {
        CredentialLease lease = leases.get(key);
        if (lease == null) {
            return null;
        }
        if (lease.isExpired(currentEpochMillis)) {
            leases.remove(key);
            return null;
        }
        return lease;
    }

    /** Removes a specific lease from the cache. */
    public void remove(String key) {
        leases.remove(key);
    }

    /** Clears all cached leases. Should be called at session end. */
    public void clear() {
        leases.clear();
    }

    /** Returns the number of entries currently in the cache. */
    public int size() {
        return leases.size();
    }
}
