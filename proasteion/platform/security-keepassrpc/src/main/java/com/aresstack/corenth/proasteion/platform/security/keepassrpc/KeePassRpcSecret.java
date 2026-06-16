package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import java.util.Arrays;

/**
 * Secret value resolved from KeePassRPC inside the adyton adapter boundary.
 */
public final class KeePassRpcSecret implements AutoCloseable {

    private final String principal;
    private final char[] secret;
    private volatile boolean closed;

    public KeePassRpcSecret(String principal, char[] secret) {
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("Principal must not be null or empty");
        }
        if (secret == null) {
            throw new IllegalArgumentException("Secret must not be null");
        }
        this.principal = principal;
        this.secret = Arrays.copyOf(secret, secret.length);
    }

    public String principal() {
        return principal;
    }

    public char[] secret() {
        if (closed) {
            return new char[0];
        }
        return Arrays.copyOf(secret, secret.length);
    }

    @Override
    public void close() {
        if (!closed) {
            Arrays.fill(secret, '\0');
            closed = true;
        }
    }

    @Override
    public String toString() {
        return "KeePassRpcSecret{principal='" + principal + "', secret=***}";
    }
}
