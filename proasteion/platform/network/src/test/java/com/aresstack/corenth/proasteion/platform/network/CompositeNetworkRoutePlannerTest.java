package com.aresstack.corenth.proasteion.platform.network;

import com.aresstack.corenth.adyton.SecretRef;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Proxy;

import static org.junit.Assert.*;

public class CompositeNetworkRoutePlannerTest {

    @Test
    public void plansDirectRouteWhenBothCapabilitiesAreDisabled() throws Exception {
        CompositeNetworkRoutePlanner planner = new CompositeNetworkRoutePlanner(null, null, NetworkAccessPolicy.direct());

        NetworkRoutePlan plan = planner.plan(request(NetworkAccessPolicy.direct()));

        assertTrue(plan.isDirectOnly());
        assertFalse(plan.hasPlatformProxy());
        assertFalse(plan.hasSecureGateway());
        assertEquals(Proxy.NO_PROXY, plan.firstHopProxy());
    }

    @Test
    public void plansPlatformProxyOnlyRoute() throws Exception {
        CompositeNetworkRoutePlanner planner = new CompositeNetworkRoutePlanner(
                new StaticPlatformProxyRouteResolver("proxy.local", 8080), null, NetworkAccessPolicy.direct());

        NetworkRoutePlan plan = planner.plan(request(NetworkAccessPolicy.platformOnly()));

        assertFalse(plan.isDirectOnly());
        assertTrue(plan.hasPlatformProxy());
        assertFalse(plan.hasSecureGateway());
        InetSocketAddress address = (InetSocketAddress) plan.firstHopProxy().address();
        assertEquals("proxy.local", address.getHostString());
        assertEquals(8080, address.getPort());
    }

    @Test
    public void plansSecureGatewayOnlyRoute() throws Exception {
        SecretRef gatewayRef = new SecretRef("keepass://gateway/default");
        CompositeNetworkRoutePlanner planner = new CompositeNetworkRoutePlanner(
                null,
                new StaticSecureGatewayRouteResolver(URI.create("https://gateway.local"), true),
                NetworkAccessPolicy.direct());

        NetworkRoutePlan plan = planner.plan(request(NetworkAccessPolicy.secureGatewayOnly(gatewayRef)));

        assertFalse(plan.hasPlatformProxy());
        assertTrue(plan.hasSecureGateway());
        assertEquals(NetworkRouteStageType.SECURE_GATEWAY, plan.stages().get(0).type());
        assertEquals(gatewayRef, plan.stages().get(0).credentialRef());
        assertTrue(plan.stages().get(0).endToEndEncryptionEnabled());
        assertEquals(Proxy.NO_PROXY, plan.firstHopProxy());
    }

    @Test
    public void composesPlatformProxyBeforeSecureGateway() throws Exception {
        SecretRef gatewayRef = new SecretRef("keepass://gateway/default");
        CompositeNetworkRoutePlanner planner = new CompositeNetworkRoutePlanner(
                new StaticPlatformProxyRouteResolver("proxy.local", 8080),
                new StaticSecureGatewayRouteResolver(URI.create("https://gateway.local"), true),
                NetworkAccessPolicy.direct());

        NetworkRoutePlan plan = planner.plan(request(NetworkAccessPolicy.platformAndSecureGateway(gatewayRef)));

        assertEquals(2, plan.stages().size());
        assertEquals(NetworkRouteStageType.PLATFORM_PROXY, plan.stages().get(0).type());
        assertEquals(NetworkRouteStageType.SECURE_GATEWAY, plan.stages().get(1).type());
    }

    @Test
    public void resolvesInheritedPolicyFromPlannerDefaults() throws Exception {
        SecretRef gatewayRef = new SecretRef("keepass://gateway/default");
        NetworkAccessPolicy defaults = NetworkAccessPolicy.platformAndSecureGateway(gatewayRef);
        CompositeNetworkRoutePlanner planner = new CompositeNetworkRoutePlanner(
                new StaticPlatformProxyRouteResolver("proxy.local", 8080),
                new StaticSecureGatewayRouteResolver(URI.create("https://gateway.local"), false),
                defaults);

        NetworkRoutePlan plan = planner.plan(request(NetworkAccessPolicy.inherit()));

        assertTrue(plan.hasPlatformProxy());
        assertTrue(plan.hasSecureGateway());
        assertEquals(gatewayRef, plan.stages().get(1).credentialRef());
    }

    private static NetworkAccessRequest request(NetworkAccessPolicy policy) {
        return new NetworkAccessRequest(URI.create("https://service.local/api"), "fetch", policy);
    }
}
