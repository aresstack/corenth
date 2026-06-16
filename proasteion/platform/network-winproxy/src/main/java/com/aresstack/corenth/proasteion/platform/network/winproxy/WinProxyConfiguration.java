package com.aresstack.corenth.proasteion.platform.network.winproxy;

/**
 * Immutable configuration for the win-proxy-java backed route resolver.
 */
public final class WinProxyConfiguration {

    private final WinProxyMode mode;
    private final String manualHost;
    private final int manualPort;
    private final String pacUrlOrScript;

    public WinProxyConfiguration(WinProxyMode mode, String manualHost, int manualPort, String pacUrlOrScript) {
        if (mode == null) {
            throw new IllegalArgumentException("WinProxy mode must not be null");
        }
        this.mode = mode;
        this.manualHost = manualHost;
        this.manualPort = manualPort;
        this.pacUrlOrScript = pacUrlOrScript;
    }

    public static WinProxyConfiguration disabled() {
        return new WinProxyConfiguration(WinProxyMode.DISABLED, null, 0, null);
    }

    public static WinProxyConfiguration windowsSettings() {
        return new WinProxyConfiguration(WinProxyMode.WINDOWS_SETTINGS, null, 0, null);
    }

    public static WinProxyConfiguration manual(String host, int port) {
        return new WinProxyConfiguration(WinProxyMode.MANUAL, host, port, null);
    }

    public static WinProxyConfiguration pacUrl(String pacUrl) {
        return new WinProxyConfiguration(WinProxyMode.PAC_URL, null, 0, pacUrl);
    }

    public static WinProxyConfiguration pacUrlScript(String pacUrlScript) {
        return new WinProxyConfiguration(WinProxyMode.PAC_URL_SCRIPT, null, 0, pacUrlScript);
    }

    public WinProxyMode mode() {
        return mode;
    }

    public String manualHost() {
        return manualHost;
    }

    public int manualPort() {
        return manualPort;
    }

    public String pacUrlOrScript() {
        return pacUrlOrScript;
    }
}
