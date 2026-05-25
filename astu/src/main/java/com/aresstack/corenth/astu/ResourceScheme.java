package com.aresstack.corenth.astu;

/**
 * Identifies the URI scheme of a virtual resource.
 *
 * <p>This is an extensible value object, not a closed enum. Well-known schemes have
 * pre-defined constants for convenience, but any scheme string is accepted via
 * {@link #of(String)}. This allows future adapters to introduce new schemes
 * (e.g. {@code "mvs"}, {@code "tn3270"}) without modifying core code.
 *
 * <p>The scheme is stored in lowercase-normalized form as required by RFC 3986 §3.1.
 *
 * <p>Adapted from MainframeMate's {@code VirtualBackendType} enum and the
 * scheme prefixes in {@code VirtualResourceRef} and {@code BookmarkEntry}.
 */
public final class ResourceScheme {

    // ── Well-known scheme constants ──────────────────────────────────────────────

    /** Standard local filesystem ({@code file:///path}). Canonical for local resources. */
    public static final ResourceScheme FILE = new ResourceScheme("file");

    /** FTP file transfer. */
    public static final ResourceScheme FTP = new ResourceScheme("ftp");

    /** Natural/NDV mainframe resources. */
    public static final ResourceScheme NDV = new ResourceScheme("ndv");

    /** Mail resources (OST/PST). */
    public static final ResourceScheme MAIL = new ResourceScheme("mail");

    /** HTTP resources. */
    public static final ResourceScheme HTTP = new ResourceScheme("http");

    /** HTTPS resources. */
    public static final ResourceScheme HTTPS = new ResourceScheme("https");

    /** SharePoint resources. */
    public static final ResourceScheme SHAREPOINT = new ResourceScheme("sharepoint");

    /** Confluence wiki pages. */
    public static final ResourceScheme CONFLUENCE = new ResourceScheme("confluence");

    /** Generic wiki pages. */
    public static final ResourceScheme WIKI = new ResourceScheme("wiki");

    // ── Instance fields ──────────────────────────────────────────────────────────

    private final String name;

    private ResourceScheme(String name) {
        this.name = name;
    }

    /**
     * Returns a {@code ResourceScheme} for an arbitrary scheme string.
     *
     * <p>The string is normalized to lowercase. If it matches a well-known constant,
     * that constant instance is returned.
     *
     * @param scheme the scheme name (e.g. {@code "file"}, {@code "ndv"}, {@code "mvs"})
     * @return a {@code ResourceScheme} instance; never null
     * @throws IllegalArgumentException if scheme is null or empty
     */
    public static ResourceScheme of(String scheme) {
        if (scheme == null || scheme.isEmpty()) {
            throw new IllegalArgumentException("Scheme must not be null or empty");
        }
        String lower = scheme.toLowerCase();
        // Return well-known constants for identity equality where possible
        if ("file".equals(lower)) return FILE;
        if ("ftp".equals(lower)) return FTP;
        if ("ndv".equals(lower)) return NDV;
        if ("mail".equals(lower)) return MAIL;
        if ("http".equals(lower)) return HTTP;
        if ("https".equals(lower)) return HTTPS;
        if ("sharepoint".equals(lower)) return SHAREPOINT;
        if ("confluence".equals(lower)) return CONFLUENCE;
        if ("wiki".equals(lower)) return WIKI;
        return new ResourceScheme(lower);
    }

    /** Returns the lowercase scheme name. */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceScheme)) return false;
        return name.equals(((ResourceScheme) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
