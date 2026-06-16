package com.aresstack.corenth.proasteion.platform.network.winproxy;

import com.aresstack.corenth.proasteion.platform.network.NetworkAccessRequest;
import com.aresstack.corenth.proasteion.platform.network.NetworkRouteStage;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutingException;
import com.aresstack.corenth.proasteion.platform.network.PlatformProxyRouteResolver;
import com.aresstack.winproxy.PacUrlSource;
import com.aresstack.winproxy.ProxyResult;
import com.aresstack.winproxy.WindowsProxyResolver;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Platform proxy resolver backed by {@code com.aresstack:win-proxy-java}.
 * <p>
 * This adapter resolves one target URL at a time and never installs a global
 * {@link java.net.ProxySelector}. Callers receive a route stage and decide per
 * connection whether to use the resulting first-hop proxy.
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
            if (configuration.mode() == WinProxyMode.PAC_URL) {
                requirePacConfiguration();
                return WindowsProxyResolver.resolve(targetUrl, PacUrlSource.DIRECT, configuration.pacUrlOrScript().trim());
            }
            if (configuration.mode() == WinProxyMode.PAC_URL_SCRIPT) {
                requirePacConfiguration();
                return WindowsProxyResolver.resolve(targetUrl, PacUrlSource.POWERSHELL, configuration.pacUrlOrScript().trim());
            }
            return WindowsProxyResolver.resolve(targetUrl);
        } catch (RuntimeException e) {
            throw new NetworkRoutingException("Windows proxy resolution failed", e);
        }
    }

    private void requirePacConfiguration() throws NetworkRoutingException {
        if (configuration.pacUrlOrScript() == null || configuration.pacUrlOrScript().trim().isEmpty()) {
            throw new NetworkRoutingException("PAC URL or PAC discovery script is missing");
        }
    }
}
