package com.aresstack.corenth.proasteion.platform.network;

/**
 * Platform proxy resolver that always resolves to DIRECT.
 */
public final class DirectPlatformProxyRouteResolver implements PlatformProxyRouteResolver {

    private final String reason;

    public DirectPlatformProxyRouteResolver() {
        this("platform-direct");
    }

    public DirectPlatformProxyRouteResolver(String reason) {
        this.reason = reason;
    }

    @Override
    public NetworkRouteStage resolvePlatformProxy(NetworkAccessRequest request) {
        return NetworkRouteStage.direct(reason);
    }
}
