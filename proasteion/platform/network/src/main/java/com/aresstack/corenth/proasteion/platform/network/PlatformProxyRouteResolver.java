package com.aresstack.corenth.proasteion.platform.network;

/**
 * Resolves the platform proxy stage for one target connection.
 */
public interface PlatformProxyRouteResolver {

    NetworkRouteStage resolvePlatformProxy(NetworkAccessRequest request) throws NetworkRoutingException;
}
