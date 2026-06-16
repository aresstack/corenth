package com.aresstack.corenth.proasteion.platform.network.winproxy;

import com.aresstack.corenth.proasteion.platform.network.NetworkAccessRequest;
import com.aresstack.corenth.proasteion.platform.network.NetworkRouteStage;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutingException;
import com.aresstack.corenth.proasteion.platform.network.PlatformProxyRouteResolver;
import com.aresstack.winproxy.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
        Object result = resolveWithWinProxyJava(request.targetUrl());
        if (isDirect(result)) {
            return NetworkRouteStage.direct("winproxy-" + readReason(result));
        }
        return NetworkRouteStage.platformProxy(readJavaProxy(result), "winproxy-" + readReason(result));
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

    private Object resolveWithWinProxyJava(String targetUrl) throws NetworkRoutingException {
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

    private Object createResolver(Class<?> resolverClass) throws Exception {
        Constructor<?> constructor = resolverClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private Object invokeResolver(Method method, Object receiver, String targetUrl) throws Exception {
        method.setAccessible(true);
        if (Modifier.isStatic(method.getModifiers())) {
            return method.invoke(null, targetUrl);
        }
        return method.invoke(receiver, targetUrl);
    }

    private boolean isDirect(Object result) throws NetworkRoutingException {
        Object value = invokeResultMethod(result, "isDirect");
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        throw new NetworkRoutingException("Windows proxy result does not expose isDirect()");
    }

    private Proxy readJavaProxy(Object result) throws NetworkRoutingException {
        Object proxy = invokeResultMethod(result, "toJavaProxy");
        if (proxy instanceof Proxy) {
            return (Proxy) proxy;
        }
        String host = readStringResult(result, "getHost", "host");
        Integer port = readIntegerResult(result, "getPort", "port");
        if (host == null || port == null) {
            throw new NetworkRoutingException("Windows proxy result does not expose a Java proxy or host/port");
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port.intValue()));
    }

    private String readReason(Object result) throws NetworkRoutingException {
        String reason = readStringResult(result, "getReason", "reason");
        return reason != null ? reason : "resolved";
    }

    private Object invokeResultMethod(Object result, String methodName) throws NetworkRoutingException {
        if (result == null) {
            throw new NetworkRoutingException("Windows proxy result must not be null");
        }
        try {
            Method method = result.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(result);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            throw new NetworkRoutingException("Windows proxy result could not be inspected", e);
        }
    }

    private String readStringResult(Object result, String getterName, String beanName) throws NetworkRoutingException {
        Object getterValue = invokeResultMethod(result, getterName);
        if (getterValue instanceof String) {
            return (String) getterValue;
        }
        Object beanValue = invokeResultMethod(result, beanName);
        if (beanValue instanceof String) {
            return (String) beanValue;
        }
        return null;
    }

    private Integer readIntegerResult(Object result, String getterName, String beanName) throws NetworkRoutingException {
        Object getterValue = invokeResultMethod(result, getterName);
        if (getterValue instanceof Number) {
            return Integer.valueOf(((Number) getterValue).intValue());
        }
        Object beanValue = invokeResultMethod(result, beanName);
        if (beanValue instanceof Number) {
            return Integer.valueOf(((Number) beanValue).intValue());
        }
        return null;
    }

    private void requirePacConfiguration() throws NetworkRoutingException {
        if (configuration.pacUrlOrScript() == null || configuration.pacUrlOrScript().trim().isEmpty()) {
            throw new NetworkRoutingException("PAC URL or PAC discovery script is missing");
        }
    }
}
