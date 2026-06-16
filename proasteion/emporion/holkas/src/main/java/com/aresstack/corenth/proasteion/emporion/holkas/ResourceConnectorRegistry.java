package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.ResourceScheme;

import java.util.Set;

/**
 * Registry for locating a raw resource connector by scheme.
 */
public interface ResourceConnectorRegistry {

    /** Returns the connector for a scheme, or {@code null} if none is registered. */
    ResourceConnector find(ResourceScheme scheme);

    /** Returns the connector for a scheme or fails with a connector exception. */
    ResourceConnector require(ResourceScheme scheme) throws ResourceConnectorException;

    /** Returns registered schemes. */
    Set<ResourceScheme> supportedSchemes();
}
