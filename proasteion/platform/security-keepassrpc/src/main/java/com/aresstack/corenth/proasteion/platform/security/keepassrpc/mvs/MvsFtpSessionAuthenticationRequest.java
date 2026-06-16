package com.aresstack.corenth.proasteion.platform.security.keepassrpc.mvs;

import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlan;

import java.util.Arrays;

/**
 * Trusted request carrying the secret only inside the security adapter boundary.
 */
public final class MvsFtpSessionAuthenticationRequest implements AutoCloseable {

    private final String targetSystem;
    private final String principal;
    private final char[] secret;
    private final NetworkRoutePlan routePlan;
    private boolean closed;

    public MvsFtpSessionAuthenticationRequest(String targetSystem, String principal,
                                              char[] secret, NetworkRoutePlan routePlan) {
        if (targetSystem == null || targetSystem.isEmpty()) {
            throw new IllegalArgumentException("targetSystem must not be null or empty");
        }
        if (principal == null || principal.isEmpty()) {
            throw new IllegalArgumentException("principal must not be null or empty");
        }
        if (secret == null) {
            throw new IllegalArgumentException("secret must not be null");
        }
        if (routePlan == null) {
            throw new IllegalArgumentException("routePlan must not be null");
        }
        this.targetSystem = targetSystem;
        this.principal = principal;
        this.secret = Arrays.copyOf(secret, secret.length);
        this.routePlan = routePlan;
    }

    public String targetSystem() {
        return targetSystem;
    }

    public String principal() {
        return principal;
    }

    public char[] secret() {
        return closed ? new char[0] : Arrays.copyOf(secret, secret.length);
    }

    public NetworkRoutePlan routePlan() {
        return routePlan;
    }

    @Override
    public void close() {
        if (!closed) {
            Arrays.fill(secret, '\0');
            closed = true;
        }
    }
}
