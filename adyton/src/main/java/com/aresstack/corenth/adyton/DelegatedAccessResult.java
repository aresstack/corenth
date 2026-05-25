package com.aresstack.corenth.adyton;

import java.util.Objects;

/**
 * The result of a delegated access operation.
 * <p>
 * Contains a status indicator and an optional opaque token. The token does not
 * contain raw secret material; it represents the outcome of a vault-mediated
 * operation (e.g., a session token, a signed assertion).
 */
public final class DelegatedAccessResult {

    private final boolean success;
    private final String token;

    private DelegatedAccessResult(boolean success, String token) {
        this.success = success;
        this.token = token;
    }

    /** Creates a successful result with the given opaque token. */
    public static DelegatedAccessResult success(String token) {
        return new DelegatedAccessResult(true, token);
    }

    /** Creates a failure result. */
    public static DelegatedAccessResult failure() {
        return new DelegatedAccessResult(false, null);
    }

    /** Returns {@code true} if the delegated operation succeeded. */
    public boolean isSuccess() {
        return success;
    }

    /** Returns the opaque result token, or {@code null} if the operation failed. */
    public String token() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DelegatedAccessResult)) return false;
        DelegatedAccessResult that = (DelegatedAccessResult) o;
        return success == that.success && Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, token);
    }

    @Override
    public String toString() {
        return "DelegatedAccessResult{success=" + success + ", token=" + (token != null ? "***" : "null") + "}";
    }
}
