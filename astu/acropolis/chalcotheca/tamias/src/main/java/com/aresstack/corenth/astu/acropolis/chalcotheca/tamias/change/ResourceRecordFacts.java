package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

/**
 * The immutable, non-persisted facts of one Chalcotheca resource record that Tamias decides on:
 * the sequence of the latest observed version, the sequence of the indexed version (if any) and
 * whether the resource was observed as removed at its source.
 *
 * <p>This is a projection of {@code ArchivedResource} (#33) that the orchestration (#10) builds from
 * a record it has read:
 * {@code latestObservedVersion().sequence()}, {@code indexedVersion().version().sequence()} or
 * {@link #NOT_INDEXED}, and {@code isRemovedAtSource()}. Comparing sequences is sufficient because
 * the record guarantees that the indexed version is part of its gap-free history. Tamias never
 * reads or writes the record, holds no history, no digests and no payload. Like the record, the
 * three facts are orthogonal: a resource may be removed at the source while its latest observed
 * version is also its indexed version.
 */
public final class ResourceRecordFacts {

    /** Marks a record without an indexed-version fact. */
    public static final long NOT_INDEXED = 0L;

    private final long latestObservedSequence;
    private final long indexedSequence;
    private final boolean removedAtSource;

    /**
     * @param latestObservedSequence the sequence of the latest observed version ({@code >= 1})
     * @param indexedSequence        the sequence of the indexed version ({@code 1..latest}) or
     *                               {@link #NOT_INDEXED}
     * @param removedAtSource        whether a removal observation (tombstone) is recorded
     */
    public ResourceRecordFacts(long latestObservedSequence, long indexedSequence, boolean removedAtSource) {
        if (latestObservedSequence < 1) {
            throw new IllegalArgumentException("latestObservedSequence must be >= 1");
        }
        if (indexedSequence < NOT_INDEXED || indexedSequence > latestObservedSequence) {
            throw new IllegalArgumentException("indexedSequence must be NOT_INDEXED or within 1.."
                    + latestObservedSequence + ", was " + indexedSequence);
        }
        this.latestObservedSequence = latestObservedSequence;
        this.indexedSequence = indexedSequence;
        this.removedAtSource = removedAtSource;
    }

    /** Return the sequence of the latest observed version. */
    public long latestObservedSequence() {
        return latestObservedSequence;
    }

    /** Return the sequence of the indexed version, or {@link #NOT_INDEXED}. */
    public long indexedSequence() {
        return indexedSequence;
    }

    /** Return whether a version is recorded as indexed. */
    public boolean isIndexed() {
        return indexedSequence != NOT_INDEXED;
    }

    /** Return whether the indexed version is the latest observed version. */
    public boolean isLatestObservedVersionIndexed() {
        return indexedSequence == latestObservedSequence;
    }

    /** Return whether a removal observation (tombstone) is recorded. */
    public boolean isRemovedAtSource() {
        return removedAtSource;
    }

    @Override
    public String toString() {
        return "ResourceRecordFacts{latest=#" + latestObservedSequence
                + ", indexed=" + (isIndexed() ? "#" + indexedSequence : "none")
                + (removedAtSource ? ", removed at source" : "")
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceRecordFacts)) return false;
        ResourceRecordFacts that = (ResourceRecordFacts) o;
        return latestObservedSequence == that.latestObservedSequence
                && indexedSequence == that.indexedSequence
                && removedAtSource == that.removedAtSource;
    }

    @Override
    public int hashCode() {
        int result = Long.valueOf(latestObservedSequence).hashCode();
        result = 31 * result + Long.valueOf(indexedSequence).hashCode();
        return 31 * result + (removedAtSource ? 1 : 0);
    }
}
