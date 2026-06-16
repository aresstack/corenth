package com.aresstack.corenth.proasteion.platform.network;

/**
 * Per-connection activation state for a route capability.
 */
public enum NetworkProxyMode {
    /** Always bypass this capability for the specific connection. */
    DISABLED,
    /** Always enable this capability for the specific connection. */
    ENABLED,
    /** Use the route planner's default policy for this capability. */
    INHERIT
}
