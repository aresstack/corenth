package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * Configuration for the vault-internal secret material cache.
 * <p>
 * Defines the caching semantics for resolved secret material and derived
 * access handles within the vault boundary.
 * <p>
 * <b>Properties:</b>
 * <ul>
 *   <li><b>TTL</b> — absolute maximum lifetime for cached entries.</li>
 *   <li><b>Idle timeout</b> — entries unused for this duration are evicted.</li>
 *   <li><b>Enabled</b> — caching can be disabled entirely.</li>
 * </ul>
 * <p>
 * <b>Migration note:</b> MainframeMate's session cache has no configurable
 * TTL or idle timeout — credentials are cached until application exit.
 * This caused real usability issues (stale credentials, KeePass instability
 * when accessed too often). Corenth's policy makes cache behavior explicit
 * and configurable.
 * <p>
 * <b>Security constraints:</b>
 * <ul>
 *   <li>Cache is RAM-only — never persisted to disk.</li>
 *   <li>Cleared on JVM shutdown and explicit broker close.</li>
 *   <li>Entries are keyed by (target, principal, purpose, scope, method).</li>
 *   <li>No logging of secret values.</li>
 * </ul>
 *
 * @see SecretMaterialCache
 * @see SessionCredentialCache
 */
public final class SecretCachePolicy {

    /** Default TTL: 60 minutes. */
    public static final long DEFAULT_TTL_MILLIS = 60 * 60 * 1000L;

    /** Default idle timeout: 10 minutes. */
    public static final long DEFAULT_IDLE_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private final boolean enabled;
    private final long ttlMillis;
    private final long idleTimeoutMillis;

    /**
     * Creates a cache policy.
     *
     * @param enabled          whether caching is enabled
     * @param ttlMillis        absolute TTL cap in millis
     * @param idleTimeoutMillis idle timeout in millis (0 = no idle eviction)
     */
    public SecretCachePolicy(boolean enabled, long ttlMillis, long idleTimeoutMillis) {
        this.enabled = enabled;
        this.ttlMillis = ttlMillis;
        this.idleTimeoutMillis = idleTimeoutMillis;
    }

    /** Returns a default policy (enabled, 60 min TTL, 10 min idle). */
    public static SecretCachePolicy defaultPolicy() {
        return new SecretCachePolicy(true, DEFAULT_TTL_MILLIS, DEFAULT_IDLE_TIMEOUT_MILLIS);
    }

    /** Returns a policy with caching disabled. */
    public static SecretCachePolicy disabled() {
        return new SecretCachePolicy(false, 0L, 0L);
    }

    /** Whether caching is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Absolute TTL cap in millis. The broker enforces this even if requestedTtlMillis is higher. */
    public long ttlMillis() {
        return ttlMillis;
    }

    /** Idle timeout in millis. Entries unused for this duration are evicted. 0 means no idle eviction. */
    public long idleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecretCachePolicy)) return false;
        SecretCachePolicy that = (SecretCachePolicy) o;
        return enabled == that.enabled
                && ttlMillis == that.ttlMillis
                && idleTimeoutMillis == that.idleTimeoutMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, ttlMillis, idleTimeoutMillis);
    }

    @Override
    public String toString() {
        return "SecretCachePolicy{enabled=" + enabled
                + ", ttlMillis=" + ttlMillis
                + ", idleTimeoutMillis=" + idleTimeoutMillis + "}";
    }
}
