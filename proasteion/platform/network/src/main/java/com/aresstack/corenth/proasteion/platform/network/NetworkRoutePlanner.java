package com.aresstack.corenth.proasteion.platform.network;

/**
 * Plans the outbound route for a single network connection.
 */
public interface NetworkRoutePlanner {

    NetworkRoutePlan plan(NetworkAccessRequest request) throws NetworkRoutingException;
}
