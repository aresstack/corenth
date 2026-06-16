package com.aresstack.corenth.proasteion.platform.network;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Simple platform proxy resolver for tests, demos and manual configurations.
 */
public final class StaticPlatformProxyRouteResolver implements PlatformProxyRouteResolver {

    private final Proxy proxy;
    private final String reason;

    public StaticPlatformProxyRouteResolver(String host, int port) {
        this(host, port, "static");
    }

    public StaticPlatformProxyRouteResolver(String host, int port, String reason) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Proxy host must not be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535");
        }
        this.proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host.trim(), port));
        this.reason = reason;
    }

    @Override
    public NetworkRouteStage resolvePlatformProxy(NetworkAccessRequest request) {
        return NetworkRouteStage.platformProxy(proxy, reason);
    }
}
