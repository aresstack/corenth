package com.aresstack.corenth.proasteion.platform.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Default route planner that composes platform proxy and secure gateway stages.
 * <p>
 * Stage order is deliberate: platform proxy first, secure gateway second. This
 * allows the gateway itself to be reached through the corporate Windows/PAC
 * proxy while still keeping both route capabilities independently switchable.
 */
public final class CompositeNetworkRoutePlanner implements NetworkRoutePlanner {

    private final PlatformProxyRouteResolver platformProxyResolver;
    private final SecureGatewayRouteResolver secureGatewayResolver;
    private final NetworkAccessPolicy defaultPolicy;

    public CompositeNetworkRoutePlanner(PlatformProxyRouteResolver platformProxyResolver,
                                        SecureGatewayRouteResolver secureGatewayResolver,
                                        NetworkAccessPolicy defaultPolicy) {
        this.platformProxyResolver = platformProxyResolver;
        this.secureGatewayResolver = secureGatewayResolver;
        this.defaultPolicy = defaultPolicy == null ? NetworkAccessPolicy.direct() : defaultPolicy;
    }

    @Override
    public NetworkRoutePlan plan(NetworkAccessRequest request) throws NetworkRoutingException {
        if (request == null) {
            throw new IllegalArgumentException("Network access request must not be null");
        }

        NetworkAccessPolicy resolvedPolicy = request.policy().resolveInheritedModes(defaultPolicy);
        NetworkAccessRequest effectiveRequest = new NetworkAccessRequest(
                request.targetUri(), request.operation(), resolvedPolicy);
        List<NetworkRouteStage> stages = new ArrayList<NetworkRouteStage>();

        if (resolvedPolicy.isPlatformProxyEnabled()) {
            requirePlatformResolver();
            appendNonDirectStage(stages, platformProxyResolver.resolvePlatformProxy(effectiveRequest));
        }

        if (resolvedPolicy.isSecureGatewayEnabled()) {
            requireSecureGatewayResolver();
            appendNonDirectStage(stages, secureGatewayResolver.resolveSecureGateway(effectiveRequest));
        }

        if (stages.isEmpty()) {
            stages.add(NetworkRouteStage.direct("direct"));
        }
        return new NetworkRoutePlan(request.targetUri(), stages);
    }

    private void appendNonDirectStage(List<NetworkRouteStage> stages, NetworkRouteStage stage) {
        if (stage != null && !stage.isDirect()) {
            stages.add(stage);
        }
    }

    private void requirePlatformResolver() throws NetworkRoutingException {
        if (platformProxyResolver == null) {
            throw new NetworkRoutingException("Platform proxy is enabled but no resolver is configured");
        }
    }

    private void requireSecureGatewayResolver() throws NetworkRoutingException {
        if (secureGatewayResolver == null) {
            throw new NetworkRoutingException("Secure gateway is enabled but no resolver is configured");
        }
    }
}
