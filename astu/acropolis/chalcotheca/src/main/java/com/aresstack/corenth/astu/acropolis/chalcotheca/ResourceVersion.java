package com.aresstack.corenth.astu.acropolis.chalcotheca;

/**
 * An immutable version record for a resource snapshot.
 *
 * <p>Captures the digest and the timestamp at which this version was observed,
 * enabling change-detection and version history tracking.
 */
public final class ResourceVersion {

    private final ResourceDigest digest;
    private final long observedAtMillis;

    public ResourceVersion(ResourceDigest digest, long observedAtMillis) {
        if (digest == null) {
            throw new IllegalArgumentException("digest must not be null");
        }
        this.digest = digest;
        this.observedAtMillis = observedAtMillis;
    }

    /** Returns the content digest for this version. */
    public ResourceDigest digest() {
        return digest;
    }

    /** Returns the epoch millis when this version was first observed. */
    public long observedAtMillis() {
        return observedAtMillis;
    }

    @Override
    public String toString() {
        return "ResourceVersion{" + digest + ", observed=" + observedAtMillis + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceVersion)) return false;
        ResourceVersion that = (ResourceVersion) o;
        return observedAtMillis == that.observedAtMillis && digest.equals(that.digest);
    }

    @Override
    public int hashCode() {
        return 31 * digest.hashCode() + Long.valueOf(observedAtMillis).hashCode();
    }
}
