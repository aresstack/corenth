package com.aresstack.corenth.astu;

/**
 * Known URI scheme prefixes for bookmark-style resource addressing.
 *
 * <p>Each scheme identifies the transport or origin category of a virtual resource
 * without coupling to any specific adapter implementation.
 */
public enum ResourceScheme {

    LOCAL("local"),
    NDV("ndv"),
    MAIL("mail"),
    SHAREPOINT("sharepoint"),
    CONFLUENCE("confluence"),
    HTTP("http"),
    HTTPS("https"),
    SOURCE("source"),
    CUSTOM("custom");

    private final String prefix;

    ResourceScheme(String prefix) {
        this.prefix = prefix;
    }

    /** Returns the scheme string used before {@code ://} in a bookmark URI. */
    public String prefix() {
        return prefix;
    }

    /**
     * Resolves a scheme from a prefix string (case-insensitive).
     *
     * @param prefix the scheme prefix, e.g. {@code "local"}
     * @return the matching {@code ResourceScheme}, or {@link #CUSTOM} if unknown
     */
    public static ResourceScheme fromPrefix(String prefix) {
        if (prefix == null) {
            return CUSTOM;
        }
        String lower = prefix.toLowerCase();
        for (ResourceScheme scheme : values()) {
            if (scheme.prefix.equals(lower)) {
                return scheme;
            }
        }
        return CUSTOM;
    }
}
