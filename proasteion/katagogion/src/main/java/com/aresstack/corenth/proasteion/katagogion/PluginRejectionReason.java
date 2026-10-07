package com.aresstack.corenth.proasteion.katagogion;

/** Typed reason why a plugin was not installed. */
public enum PluginRejectionReason {

    /** The plugin descriptor or tool list could not be read or was invalid. */
    INVALID_PLUGIN,

    /** A plugin with the same id is already installed. */
    DUPLICATE_PLUGIN,

    /** The admission policy rejected the plugin. */
    NOT_ADMITTED,

    /** A tool requires a capability that its plugin did not request or was not granted. */
    CAPABILITY_NOT_GRANTED,

    /** A granted capability has no implementation in this host. */
    CAPABILITY_UNAVAILABLE,

    /** A tool name is already taken by an installed tool or repeated within the plugin. */
    DUPLICATE_TOOL
}
