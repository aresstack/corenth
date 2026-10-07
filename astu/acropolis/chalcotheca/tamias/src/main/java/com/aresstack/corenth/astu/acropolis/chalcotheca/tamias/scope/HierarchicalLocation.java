package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lexical, scheme-neutral view of a {@link BookmarkUri} as scheme, authority and normalized path
 * segments, so that containment and depth work the same for {@code file:}, {@code ftp:},
 * {@code ndv:} and other schemes. No file system or network access happens here.
 *
 * <p>Standard URIs contribute their (lower-cased) authority and decoded path; query and fragment
 * do not take part in scoping. Opaque schemes contribute their scheme-specific part as path.
 * Empty and {@code .} segments are dropped and {@code ..} removes the previous segment; a
 * {@code ..} that would climb above the first segment marks the location as escaping, and an
 * escaping location is never contained in any scope.
 */
final class HierarchicalLocation {

    private final ResourceScheme scheme;
    private final String authority;
    private final List<String> segments;
    private final boolean escaping;

    private HierarchicalLocation(ResourceScheme scheme, String authority, List<String> segments, boolean escaping) {
        this.scheme = scheme;
        this.authority = authority;
        this.segments = segments;
        this.escaping = escaping;
    }

    static HierarchicalLocation of(BookmarkUri uri) {
        URI standard = uri.toURI();
        String authority = "";
        String path = uri.schemeSpecificPart();
        if (standard != null && !standard.isOpaque()) {
            authority = standard.getAuthority() == null ? "" : standard.getAuthority().toLowerCase(Locale.ROOT);
            path = standard.getPath() == null ? "" : standard.getPath();
        }
        List<String> segments = new ArrayList<String>();
        boolean escaping = false;
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    escaping = true;
                } else {
                    segments.remove(segments.size() - 1);
                }
                continue;
            }
            segments.add(segment);
        }
        return new HierarchicalLocation(uri.scheme(), authority, Collections.unmodifiableList(segments), escaping);
    }

    /**
     * Return how many segments {@code other} lies below this location, or {@code -1} if it does
     * not lie at or below it.
     */
    int depthOf(HierarchicalLocation other) {
        if (escaping || other.escaping
                || !scheme.equals(other.scheme)
                || !authority.equals(other.authority)
                || other.segments.size() < segments.size()) {
            return -1;
        }
        for (int i = 0; i < segments.size(); i++) {
            if (!segments.get(i).equals(other.segments.get(i))) {
                return -1;
            }
        }
        return other.segments.size() - segments.size();
    }
}
