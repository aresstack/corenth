package com.aresstack.corenth.proasteion.platform.network;

import com.aresstack.corenth.adyton.SecretRef;

import java.util.Objects;

/**
 * Per-resource network policy for route planning.
 * <p>
 * Platform proxy and secure gateway are independent switches. A connection may
 * use neither, exactly one, or both in a deterministic order.
 */
public final class NetworkAccessPolicy {

    private final NetworkProxyMode platformProxyMode;
    private final NetworkProxyMode secureGatewayMode;
    private final SecretRef secureGatewayCredentialRef;

    public NetworkAccessPolicy(NetworkProxyMode platformProxyMode,
                               NetworkProxyMode secureGatewayMode,
                               SecretRef secureGatewayCredentialRef) {
        if (platformProxyMode == null) {
            throw new IllegalArgumentException("Platform proxy mode must not be null");
        }
        if (secureGatewayMode == null) {
            throw new IllegalArgumentException("Secure gateway mode must not be null");
        }
        this.platformProxyMode = platformProxyMode;
        this.secureGatewayMode = secureGatewayMode;
        this.secureGatewayCredentialRef = secureGatewayCredentialRef;
    }

    public static NetworkAccessPolicy direct() {
        return new NetworkAccessPolicy(NetworkProxyMode.DISABLED, NetworkProxyMode.DISABLED, null);
    }

    public static NetworkAccessPolicy inherit() {
        return new NetworkAccessPolicy(NetworkProxyMode.INHERIT, NetworkProxyMode.INHERIT, null);
    }

    public static NetworkAccessPolicy platformOnly() {
        return new NetworkAccessPolicy(NetworkProxyMode.ENABLED, NetworkProxyMode.DISABLED, null);
    }

    public static NetworkAccessPolicy secureGatewayOnly(SecretRef secureGatewayCredentialRef) {
        return new NetworkAccessPolicy(NetworkProxyMode.DISABLED, NetworkProxyMode.ENABLED, secureGatewayCredentialRef);
    }

    public static NetworkAccessPolicy platformAndSecureGateway(SecretRef secureGatewayCredentialRef) {
        return new NetworkAccessPolicy(NetworkProxyMode.ENABLED, NetworkProxyMode.ENABLED, secureGatewayCredentialRef);
    }

    public NetworkProxyMode platformProxyMode() {
        return platformProxyMode;
    }

    public NetworkProxyMode secureGatewayMode() {
        return secureGatewayMode;
    }

    public SecretRef secureGatewayCredentialRef() {
        return secureGatewayCredentialRef;
    }

    public NetworkAccessPolicy resolveInheritedModes(NetworkAccessPolicy defaults) {
        if (defaults == null) {
            defaults = direct();
        }
        NetworkProxyMode resolvedPlatform = resolveMode(platformProxyMode, defaults.platformProxyMode());
        NetworkProxyMode resolvedGateway = resolveMode(secureGatewayMode, defaults.secureGatewayMode());
        SecretRef resolvedGatewayRef = secureGatewayCredentialRef != null
                ? secureGatewayCredentialRef
                : defaults.secureGatewayCredentialRef();
        return new NetworkAccessPolicy(resolvedPlatform, resolvedGateway, resolvedGatewayRef);
    }

    public boolean isPlatformProxyEnabled() {
        return platformProxyMode == NetworkProxyMode.ENABLED;
    }

    public boolean isSecureGatewayEnabled() {
        return secureGatewayMode == NetworkProxyMode.ENABLED;
    }

    private static NetworkProxyMode resolveMode(NetworkProxyMode mode, NetworkProxyMode fallback) {
        if (mode != NetworkProxyMode.INHERIT) {
            return mode;
        }
        if (fallback == null || fallback == NetworkProxyMode.INHERIT) {
            return NetworkProxyMode.DISABLED;
        }
        return fallback;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkAccessPolicy)) return false;
        NetworkAccessPolicy that = (NetworkAccessPolicy) o;
        return platformProxyMode == that.platformProxyMode
                && secureGatewayMode == that.secureGatewayMode
                && Objects.equals(secureGatewayCredentialRef, that.secureGatewayCredentialRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platformProxyMode, secureGatewayMode, secureGatewayCredentialRef);
    }

    @Override
    public String toString() {
        return "NetworkAccessPolicy{platformProxyMode=" + platformProxyMode
                + ", secureGatewayMode=" + secureGatewayMode
                + ", secureGatewayCredentialRef=" + (secureGatewayCredentialRef == null ? "none" : "***")
                + "}";
    }
}
