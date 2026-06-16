package com.aresstack.corenth.proasteion.platform.network.winproxy;

/**
 * Configuration mode for the Windows proxy resolver adapter.
 */
public enum WinProxyMode {
    DISABLED,
    WINDOWS_SETTINGS,
    PAC_URL,
    PAC_URL_SCRIPT,
    MANUAL
}
