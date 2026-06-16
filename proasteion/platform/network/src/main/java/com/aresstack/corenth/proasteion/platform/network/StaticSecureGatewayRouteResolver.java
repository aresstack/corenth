package com.aresstack.corenth.proasteion.platform.network;

import com.aresstack.corenth.adyton.SecretRef;

import java.net.URI;

/**
 * Secure-gateway resolver for configured single-gateway deployments.
 */
public final class StaticSecureGatewayRouteResolver implements SecureGatewayRouteResolver {

    private final URI gatewayUri;
    private final boolean endToEndEncryptionEnabled;
    private final String reason;

    public StaticSecureGatewayRouteResolver(URI gatewayUri, boolean endToEndEncryptionEnabled) {
        this(gatewayUri, endToEndEncryptionEnabled, "secure-gateway");
    }

    public StaticSecureGatewayRouteResolver(URI gatewayUri, boolean endToEndEncryptionEnabled, String reason) {
        if (gatewayUri == null) {
            throw new IllegalArgumentException("Gateway URI must not be null");
        }
        this.gatewayUri = gatewayUri;
        this.endToEndEncryptionEnabled = endToEndEncryptionEnabled;
        this.reason = reason;
    }

    @Override
    public NetworkRouteStage resolveSecureGateway(NetworkAccessRequest request) throws NetworkRoutingException {
        SecretRef credentialRef = request.policy().secureGatewayCredentialRef();
        if (credentialRef == null) {
            throw new NetworkRoutingException("Secure gateway is enabled but no credential reference is configured");
        }
        return NetworkRouteStage.secureGateway(gatewayUri, credentialRef, endToEndEncryptionEnabled, reason);
    }
}
