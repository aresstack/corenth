package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import com.aresstack.corenth.astu.BookmarkUri;

/**
 * An immutable resource scope: the subtree at and below one root {@link BookmarkUri}.
 *
 * <p>Containment is decided lexically on scheme, authority and whole path segments, never by a
 * string prefix ({@code /data} does not contain {@code /database}). Path segments are compared
 * case-sensitively. Include/exclude patterns are not part of a scope; they stay with the existing
 * {@code PatternResourcePolicy}/{@code IndexingRule} and are combined by the caller.
 *
 * <p>Several roots are expressed as several scopes; there is deliberately no collection object.
 */
public final class ResourceScope {

    /** Returned by {@link #depthOf(BookmarkUri)} for a resource outside the scope. */
    public static final int NOT_IN_SCOPE = -1;

    private final BookmarkUri root;
    private final HierarchicalLocation rootLocation;

    private ResourceScope(BookmarkUri root) {
        this.root = root;
        this.rootLocation = HierarchicalLocation.of(root);
    }

    /**
     * @param root the scope root; the root itself is part of the scope at depth 0
     * @return the scope at and below {@code root}
     */
    public static ResourceScope of(BookmarkUri root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        return new ResourceScope(root);
    }

    /** Return the scope root. */
    public BookmarkUri root() {
        return root;
    }

    /**
     * Return how many path segments {@code uri} lies below the root (0 for the root itself), or
     * {@link #NOT_IN_SCOPE}.
     */
    public int depthOf(BookmarkUri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        return rootLocation.depthOf(HierarchicalLocation.of(uri));
    }

    /** Return whether {@code uri} lies at or below the root. */
    public boolean contains(BookmarkUri uri) {
        return depthOf(uri) != NOT_IN_SCOPE;
    }

    /** Decide whether {@code uri} is inside this scope ({@code IN_SCOPE}/{@code OUT_OF_SCOPE}). */
    public ScopeDecision evaluate(BookmarkUri uri) {
        return contains(uri)
                ? new ScopeDecision(ScopeReasonCode.IN_SCOPE, uri + " is inside scope " + root)
                : new ScopeDecision(ScopeReasonCode.OUT_OF_SCOPE, uri + " is outside scope " + root);
    }

    @Override
    public String toString() {
        return "ResourceScope{" + root + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceScope)) return false;
        return root.equals(((ResourceScope) o).root);
    }

    @Override
    public int hashCode() {
        return root.hashCode();
    }
}
