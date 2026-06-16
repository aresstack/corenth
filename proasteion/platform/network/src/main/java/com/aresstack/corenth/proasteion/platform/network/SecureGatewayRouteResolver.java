package com.aresstack.corenth.proasteion.platform.network;

/**
 * Resolves the authenticated secure-gateway stage for one target connection.
 */
public interface SecureGatewayRouteResolver {

    NetworkRouteStage resolveSecureGateway(NetworkAccessRequest request) throws NetworkRoutingException;
}
