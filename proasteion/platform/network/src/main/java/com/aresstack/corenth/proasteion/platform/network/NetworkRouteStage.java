package com.aresstack.corenth.proasteion.platform.network;

import com.aresstack.corenth.adyton.SecretRef;

import java.net.Proxy;
import java.net.URI;
import java.util.Objects;

/**
 * One immutable stage in an outbound route.
 * <p>
 * The stage may describe direct access, a classic Java proxy or a secure
 * gateway/tunnel. Credentials are represented only as opaque {@link SecretRef}
 * values and must be resolved through adyton by the concrete transport adapter.
 */
public final class NetworkRouteStage {

    private final NetworkRouteStageType type;
    private final String reason;
    private final Proxy proxy;
    private final URI endpointUri;
    private final SecretRef credentialRef;
    private final boolean endToEndEncryptionEnabled;

    private NetworkRouteStage(NetworkRouteStageType type, String reason, Proxy proxy,
                              URI endpointUri, SecretRef credentialRef,
                              boolean endToEndEncryptionEnabled) {
        if (type == null) {
            throw new IllegalArgumentException("Route stage type must not be null");
        }
        this.type = type;
        this.reason = reason;
        this.proxy = proxy;
        this.endpointUri = endpointUri;
        this.credentialRef = credentialRef;
        this.endToEndEncryptionEnabled = endToEndEncryptionEnabled;
    }

    public static NetworkRouteStage direct(String reason) {
        return new NetworkRouteStage(NetworkRouteStageType.DIRECT, reason, Proxy.NO_PROXY, null, null, false);
    }

    public static NetworkRouteStage platformProxy(Proxy proxy, String reason) {
        if (proxy == null) {
            throw new IllegalArgumentException("Platform proxy must not be null");
        }
        return new NetworkRouteStage(NetworkRouteStageType.PLATFORM_PROXY, reason, proxy, null, null, false);
    }

    public static NetworkRouteStage secureGateway(URI endpointUri, SecretRef credentialRef,
                                                  boolean endToEndEncryptionEnabled, String reason) {
        if (endpointUri == null) {
            throw new IllegalArgumentException("Secure gateway endpoint must not be null");
        }
        return new NetworkRouteStage(NetworkRouteStageType.SECURE_GATEWAY, reason, Proxy.NO_PROXY,
                endpointUri, credentialRef, endToEndEncryptionEnabled);
    }

    public NetworkRouteStageType type() {
        return type;
    }

    public String reason() {
        return reason;
    }

    public Proxy proxy() {
        return proxy;
    }

    public URI endpointUri() {
        return endpointUri;
    }

    public SecretRef credentialRef() {
        return credentialRef;
    }

    public boolean endToEndEncryptionEnabled() {
        return endToEndEncryptionEnabled;
    }

    public boolean isDirect() {
        return type == NetworkRouteStageType.DIRECT;
    }

    public boolean isPlatformProxy() {
        return type == NetworkRouteStageType.PLATFORM_PROXY;
    }

    public boolean isSecureGateway() {
        return type == NetworkRouteStageType.SECURE_GATEWAY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkRouteStage)) return false;
        NetworkRouteStage that = (NetworkRouteStage) o;
        return endToEndEncryptionEnabled == that.endToEndEncryptionEnabled
                && type == that.type
                && Objects.equals(reason, that.reason)
                && Objects.equals(proxy, that.proxy)
                && Objects.equals(endpointUri, that.endpointUri)
                && Objects.equals(credentialRef, that.credentialRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, reason, proxy, endpointUri, credentialRef, endToEndEncryptionEnabled);
    }

    @Override
    public String toString() {
        return "NetworkRouteStage{type=" + type
                + ", reason='" + reason + '\''
                + ", endpointUri=" + endpointUri
                + ", credentialRef=" + (credentialRef == null ? "none" : "***")
                + ", e2e=" + endToEndEncryptionEnabled
                + "}";
    }
}
