package com.aresstack.corenth.astu;

/**
 * A reference to the actual content of a virtual resource.
 *
 * <p>This represents a pointer to where content can be obtained — for example
 * a local cache path, a stream identifier, or a retrieval key — without
 * embedding the content itself. It decouples the resource identity from its
 * payload delivery.
 */
public final class ResourceContentRef {

    private final String location;
    private final String storageHint;

    /**
     * @param location    an opaque location string understood by the content backend
     * @param storageHint an optional hint about storage format (e.g. "raw", "compressed"); may be null
     */
    public ResourceContentRef(String location, String storageHint) {
        if (location == null || location.isEmpty()) {
            throw new IllegalArgumentException("Location must not be null or empty");
        }
        this.location = location;
        this.storageHint = storageHint;
    }

    /** Returns the opaque content location. */
    public String location() {
        return location;
    }

    /** Returns the optional storage format hint, or {@code null}. */
    public String storageHint() {
        return storageHint;
    }

    @Override
    public String toString() {
        return "ResourceContentRef{" + location +
                (storageHint != null ? ", hint=" + storageHint : "") + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceContentRef)) return false;
        ResourceContentRef that = (ResourceContentRef) o;
        if (!location.equals(that.location)) return false;
        return storageHint != null ? storageHint.equals(that.storageHint) : that.storageHint == null;
    }

    @Override
    public int hashCode() {
        int result = location.hashCode();
        result = 31 * result + (storageHint != null ? storageHint.hashCode() : 0);
        return result;
    }
}
