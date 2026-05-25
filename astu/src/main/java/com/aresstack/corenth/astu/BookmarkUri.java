package com.aresstack.corenth.astu;

/**
 * A bookmark-style URI that addresses a virtual resource.
 *
 * <p>Format: {@code scheme://path} where scheme identifies the transport category
 * and path is an opaque, scheme-specific locator.
 *
 * <p>Examples:
 * <pre>
 *   local://documents/specification.pdf
 *   ndv://mainframe/system/program.cgp
 *   mail://inbox/2024-03-01/subject
 * </pre>
 *
 * <p>This class is immutable and safe for use as a map key.
 */
public final class BookmarkUri {

    private static final String SEPARATOR = "://";

    private final ResourceScheme scheme;
    private final String rawScheme;
    private final String path;

    private BookmarkUri(String rawScheme, ResourceScheme scheme, String path) {
        this.rawScheme = rawScheme;
        this.scheme = scheme;
        this.path = path;
    }

    /**
     * Parses a bookmark URI string into its components.
     *
     * @param uri the full URI string, e.g. {@code "local://documents/file.pdf"}
     * @return a parsed {@code BookmarkUri}
     * @throws IllegalArgumentException if the URI is null, empty or lacks a {@code ://} separator
     */
    public static BookmarkUri parse(String uri) {
        if (uri == null || uri.isEmpty()) {
            throw new IllegalArgumentException("Bookmark URI must not be null or empty");
        }
        int idx = uri.indexOf(SEPARATOR);
        if (idx <= 0) {
            throw new IllegalArgumentException(
                    "Bookmark URI must contain a scheme followed by '://' — got: " + uri);
        }
        String rawScheme = uri.substring(0, idx);
        String path = uri.substring(idx + SEPARATOR.length());
        ResourceScheme scheme = ResourceScheme.fromPrefix(rawScheme);
        return new BookmarkUri(rawScheme, scheme, path);
    }

    /**
     * Constructs a bookmark URI from explicit parts.
     *
     * @param scheme the resource scheme
     * @param path   the scheme-specific path
     * @return a new {@code BookmarkUri}
     */
    public static BookmarkUri of(ResourceScheme scheme, String path) {
        if (scheme == null) {
            throw new IllegalArgumentException("Scheme must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        return new BookmarkUri(scheme.prefix(), scheme, path);
    }

    /** Returns the resolved scheme category. */
    public ResourceScheme scheme() {
        return scheme;
    }

    /** Returns the raw scheme string as it appeared in the URI. */
    public String rawScheme() {
        return rawScheme;
    }

    /** Returns the path portion after {@code ://}. */
    public String path() {
        return path;
    }

    /** Returns the full URI string representation. */
    @Override
    public String toString() {
        return rawScheme + SEPARATOR + path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookmarkUri)) return false;
        BookmarkUri that = (BookmarkUri) o;
        return rawScheme.equals(that.rawScheme) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return 31 * rawScheme.hashCode() + path.hashCode();
    }
}
