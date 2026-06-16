package com.aresstack.corenth.proasteion.platform.network.winproxy;

import com.aresstack.corenth.proasteion.platform.network.NetworkAccessRequest;
import com.aresstack.corenth.proasteion.platform.network.NetworkRouteStage;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutingException;
import com.aresstack.corenth.proasteion.platform.network.PlatformProxyRouteResolver;
import com.aresstack.winproxy.*;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Platform proxy resolver backed by the win-proxy Java library.
 * <p>
 * Resolve one target URL at a time and never install a global ProxySelector.
 */
public final class WinProxyPlatformProxyRouteResolver implements PlatformProxyRouteResolver {

    private final WinProxyConfiguration configuration;

    public WinProxyPlatformProxyRouteResolver(WinProxyConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("WinProxy configuration must not be null");
        }
        this.configuration = configuration;
    }

    @Override
    public NetworkRouteStage resolvePlatformProxy(NetworkAccessRequest request) throws NetworkRoutingException {
        if (request == null) {
            throw new IllegalArgumentException("Network access request must not be null");
        }
        if (configuration.mode() == WinProxyMode.DISABLED) {
            return NetworkRouteStage.direct("winproxy-disabled");
        }
        if (configuration.mode() == WinProxyMode.MANUAL) {
            return resolveManualProxy();
        }

        ProxyResult result = resolveWithWinProxyJava(request.targetUrl());
        if (result.isDirect()) {
            return NetworkRouteStage.direct("winproxy-" + result.getReason());
        }
        return NetworkRouteStage.platformProxy(result.toJavaProxy(), "winproxy-" + result.getReason());
    }

    private NetworkRouteStage resolveManualProxy() throws NetworkRoutingException {
        String host = configuration.manualHost();
        int port = configuration.manualPort();
        if (host == null || host.trim().isEmpty()) {
            throw new NetworkRoutingException("Manual platform proxy host is missing");
        }
        if (port <= 0 || port > 65535) {
            throw new NetworkRoutingException("Manual platform proxy port is invalid: " + port);
        }
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host.trim(), port));
        return NetworkRouteStage.platformProxy(proxy, "winproxy-manual");
    }

    private ProxyResult resolveWithWinProxyJava(String targetUrl) throws NetworkRoutingException {
        try {
            ProxyConfiguration proxyConfiguration = createWinProxyConfiguration();
            WindowsProxyResolver resolver = new WindowsProxyResolver(proxyConfiguration);
            return resolver.resolve(targetUrl);
        } catch (RuntimeException e) {
            throw new NetworkRoutingException("Windows proxy resolution failed", e);
        }
    }

    private ProxyConfiguration createWinProxyConfiguration() throws NetworkRoutingException {
        ProxyConfiguration.Builder builder = ProxyConfiguration.builder()
                .mode(toWinProxyJavaMode(configuration.mode()));
        if (configuration.mode() == WinProxyMode.PAC_URL) {
            requirePacConfiguration();
            builder.pacUrl(configuration.pacUrlOrScript().trim());
        }
        if (configuration.mode() == WinProxyMode.PAC_URL_SCRIPT) {
            requirePacConfiguration();
            builder.pacUrlDiscoveryScript(configuration.pacUrlOrScript().trim());
        }
        return builder.build();
    }

    private ProxyMode toWinProxyJavaMode(WinProxyMode mode) {
        if (mode == WinProxyMode.DISABLED) {
            return ProxyMode.DISABLED;
        }
        if (mode == WinProxyMode.PAC_URL) {
            return ProxyMode.PAC_URL_MANUAL;
        }
        if (mode == WinProxyMode.PAC_URL_SCRIPT) {
            return ProxyMode.PAC_URL_POWERSHELL;
        }
        return ProxyMode.PAC_URL_WINDOWS_SETTINGS;
    }

    private void requirePacConfiguration() throws NetworkRoutingException {
        if (configuration.pacUrlOrScript() == null || configuration.pacUrlOrScript().trim().isEmpty()) {
            throw new NetworkRoutingException("PAC URL or PAC discovery script is missing");
        }
    }
}
