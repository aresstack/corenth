package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * A resource tracked by the chalcotheca archive.
 *
 * <p>Represents the full lifecycle metadata for a single virtual resource,
 * regardless of its origin (local file, mail, SharePoint, Confluence,
 * mainframe, web page, source artifact, etc.).
 *
 * <p>This is the primary aggregate stored by {@link ResourceArchiveRepository}.
 */
public final class ArchivedResource {

    private final VirtualResourceRef ref;
    private ResourceLifecycleState state;
    private ResourceVersion currentVersion;
    private long firstSeenMillis;
    private long lastSeenMillis;
    private long tombstonedAtMillis;

    public ArchivedResource(VirtualResourceRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        this.ref = ref;
        this.state = ResourceLifecycleState.PENDING;
    }

    /** Returns the resource reference. */
    public VirtualResourceRef ref() {
        return ref;
    }

    /** Returns the current lifecycle state. */
    public ResourceLifecycleState state() {
        return state;
    }

    /** Updates the lifecycle state. */
    public void setState(ResourceLifecycleState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.state = state;
    }

    /** Returns the most recent version, or {@code null} if none recorded. */
    public ResourceVersion currentVersion() {
        return currentVersion;
    }

    /** Sets the most recent version. */
    public void setCurrentVersion(ResourceVersion version) {
        this.currentVersion = version;
    }

    /** Returns the epoch millis when this resource was first seen. */
    public long firstSeenMillis() {
        return firstSeenMillis;
    }

    /** Sets the epoch millis when this resource was first seen. */
    public void setFirstSeenMillis(long firstSeenMillis) {
        this.firstSeenMillis = firstSeenMillis;
    }

    /** Returns the epoch millis when this resource was most recently seen. */
    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    /** Sets the epoch millis when this resource was most recently seen. */
    public void setLastSeenMillis(long lastSeenMillis) {
        this.lastSeenMillis = lastSeenMillis;
    }

    /** Returns the epoch millis when this resource was tombstoned, or 0 if active. */
    public long tombstonedAtMillis() {
        return tombstonedAtMillis;
    }

    /** Marks this resource as tombstoned at the given time. */
    public void tombstone(long atMillis) {
        this.tombstonedAtMillis = atMillis;
        this.state = ResourceLifecycleState.TOMBSTONED;
    }

    @Override
    public String toString() {
        return "ArchivedResource{" + ref + ", state=" + state + "}";
    }
}
