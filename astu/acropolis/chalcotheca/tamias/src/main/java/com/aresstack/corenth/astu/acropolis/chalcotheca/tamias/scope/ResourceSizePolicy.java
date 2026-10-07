package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;

/**
 * Immutable size limit for one requested operation (for example indexing or acquisition); a
 * caller that needs different limits per operation holds one policy per operation.
 *
 * <p>A size exactly at the limit is admitted. A negative size, including
 * {@link ResourcePolicy#SIZE_UNKNOWN} used before acquisition, is never treated as an empty
 * resource: with a configured limit the decision is {@link ScopeReasonCode#SIZE_UNKNOWN}
 * ({@link ScopeVerdict#UNDETERMINED}) and has to be repeated with the real size.
 *
 * <p>Relation to {@code IndexingRule.maxBytes}: since #10 Slice 5 the production composition
 * configures the indexing size limit only here; the lifecycle applies it to source metadata, to
 * the bounded acquisition and to the acquired size. The rule-level limit keeps its semantics
 * inside {@code PatternResourcePolicy} for existing callers; the two are not merged.
 */
public final class ResourceSizePolicy {

    private static final long NO_LIMIT = Long.MAX_VALUE;

    private final long maxBytes;

    private ResourceSizePolicy(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /** Return a policy without a size limit. */
    public static ResourceSizePolicy unlimited() {
        return new ResourceSizePolicy(NO_LIMIT);
    }

    /**
     * @param maxBytes the largest admitted size in bytes; must be {@code >= 0}
     * @return a policy admitting sizes up to and including {@code maxBytes}
     */
    public static ResourceSizePolicy maxBytes(long maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be >= 0");
        }
        return new ResourceSizePolicy(maxBytes);
    }

    /** Return whether a limit is configured. */
    public boolean isLimited() {
        return maxBytes != NO_LIMIT;
    }

    /** Return the configured limit; only meaningful if {@link #isLimited()}. */
    public long limitBytes() {
        return maxBytes;
    }

    /**
     * Decide whether a resource of the given size is admitted
     * ({@code NO_SIZE_LIMIT}, {@code SIZE_UNKNOWN}, {@code SIZE_OVER_LIMIT} or {@code SIZE_WITHIN_LIMIT}).
     *
     * @param sizeBytes the size in bytes, or a negative value such as
     *                  {@link ResourcePolicy#SIZE_UNKNOWN} when it is not known yet
     */
    public ScopeDecision evaluate(long sizeBytes) {
        if (!isLimited()) {
            return new ScopeDecision(ScopeReasonCode.NO_SIZE_LIMIT, "no size limit configured");
        }
        if (sizeBytes < 0) {
            return new ScopeDecision(ScopeReasonCode.SIZE_UNKNOWN,
                    "size not known yet; limit " + maxBytes + " bytes must be checked again");
        }
        if (sizeBytes > maxBytes) {
            return new ScopeDecision(ScopeReasonCode.SIZE_OVER_LIMIT,
                    sizeBytes + " bytes > limit " + maxBytes + " bytes");
        }
        return new ScopeDecision(ScopeReasonCode.SIZE_WITHIN_LIMIT,
                sizeBytes + " bytes <= limit " + maxBytes + " bytes");
    }

    @Override
    public String toString() {
        return "ResourceSizePolicy{" + (isLimited() ? maxBytes + " bytes" : "unlimited") + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceSizePolicy)) return false;
        return maxBytes == ((ResourceSizePolicy) o).maxBytes;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(maxBytes).hashCode();
    }
}
