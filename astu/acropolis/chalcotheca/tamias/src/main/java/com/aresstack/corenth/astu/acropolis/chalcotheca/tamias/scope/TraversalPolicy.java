package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import com.aresstack.corenth.astu.BookmarkUri;

/**
 * Immutable traversal policy: how deep below a {@link ResourceScope} root a traversal may go.
 *
 * <p>Depth counts path segments below the root: the root has depth 0, its direct children depth 1.
 * A resource at exactly {@code maxDepth} is admitted; a container at {@code maxDepth} may be
 * read but not descended into. Resources outside the scope are rejected with
 * {@link ScopeReasonCode#OUT_OF_SCOPE} before depth is considered.
 */
public final class TraversalPolicy {

    /** Marks a traversal without a depth limit. */
    public static final int UNLIMITED = -1;

    private final ResourceScope scope;
    private final int maxDepth;

    /**
     * @param scope    the scope whose root depth is counted from
     * @param maxDepth the maximum depth ({@code >= 0}) or {@link #UNLIMITED}
     */
    public TraversalPolicy(ResourceScope scope, int maxDepth) {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (maxDepth < 0 && maxDepth != UNLIMITED) {
            throw new IllegalArgumentException("maxDepth must be >= 0 or UNLIMITED");
        }
        this.scope = scope;
        this.maxDepth = maxDepth;
    }

    /** Return a traversal of the whole scope without a depth limit. */
    public static TraversalPolicy unlimited(ResourceScope scope) {
        return new TraversalPolicy(scope, UNLIMITED);
    }

    /** Return the scope. */
    public ResourceScope scope() {
        return scope;
    }

    /** Return the maximum depth, or {@link #UNLIMITED}. */
    public int maxDepth() {
        return maxDepth;
    }

    /** Return whether the traversal has a depth limit. */
    public boolean isLimited() {
        return maxDepth != UNLIMITED;
    }

    /**
     * Decide whether the traversal may reach {@code uri}
     * ({@code OUT_OF_SCOPE}, {@code DEPTH_EXCEEDED} or {@code WITHIN_DEPTH}).
     */
    public ScopeDecision evaluate(BookmarkUri uri) {
        int depth = scope.depthOf(uri);
        if (depth == ResourceScope.NOT_IN_SCOPE) {
            return scope.evaluate(uri);
        }
        if (isLimited() && depth > maxDepth) {
            return new ScopeDecision(ScopeReasonCode.DEPTH_EXCEEDED,
                    uri + " lies at depth " + depth + " > max depth " + maxDepth);
        }
        return new ScopeDecision(ScopeReasonCode.WITHIN_DEPTH,
                uri + " lies at depth " + depth + describeLimit());
    }

    /**
     * Decide whether the traversal may list the children of the container {@code uri}
     * ({@code OUT_OF_SCOPE}, {@code DEPTH_EXHAUSTED} or {@code DESCENT_ALLOWED}).
     */
    public ScopeDecision evaluateDescent(BookmarkUri container) {
        int depth = scope.depthOf(container);
        if (depth == ResourceScope.NOT_IN_SCOPE) {
            return scope.evaluate(container);
        }
        if (isLimited() && depth >= maxDepth) {
            return new ScopeDecision(ScopeReasonCode.DEPTH_EXHAUSTED,
                    "children of " + container + " would lie at depth " + (depth + 1) + " > max depth " + maxDepth);
        }
        return new ScopeDecision(ScopeReasonCode.DESCENT_ALLOWED,
                "children of " + container + " lie at depth " + (depth + 1) + describeLimit());
    }

    private String describeLimit() {
        return isLimited() ? " <= max depth " + maxDepth : " (no depth limit)";
    }

    @Override
    public String toString() {
        return "TraversalPolicy{" + scope + ", maxDepth=" + (isLimited() ? String.valueOf(maxDepth) : "unlimited") + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TraversalPolicy)) return false;
        TraversalPolicy that = (TraversalPolicy) o;
        return maxDepth == that.maxDepth && scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
        return 31 * scope.hashCode() + maxDepth;
    }
}
