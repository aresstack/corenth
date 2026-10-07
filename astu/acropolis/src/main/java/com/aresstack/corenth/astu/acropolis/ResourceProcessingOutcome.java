package com.aresstack.corenth.astu.acropolis;

/**
 * Fine-grained final outcome of processing one resource (#10 Slice 4).
 *
 * <p>The outcome is a run fact, not a resource fact: it is never written into the resource
 * record (#33), so a failed or denied run does not turn the history into a FAILED or DENIED
 * state.
 */
public enum ResourceProcessingOutcome {
    /** The resource was (re)indexed. */
    INDEXED,
    /** Content and index are current; nothing was written. */
    UNCHANGED,
    /** An access or indexing policy withheld the resource. */
    DENIED,
    /** Extraction worked but yielded no indexable text. */
    NO_EXTRACTABLE_CONTENT,
    /** The resource was confirmed removed at the source; derived state was withdrawn if indexed. */
    REMOVED,
    /** The user cancelled a credential request. */
    CANCELLED,
    /** A step failed. */
    FAILED;

    /** Returns the coarse {@link ProcessingResult.Status} for this outcome. */
    public ProcessingResult.Status status() {
        switch (this) {
            case INDEXED:
                return ProcessingResult.Status.INDEXED;
            case UNCHANGED:
                return ProcessingResult.Status.UNCHANGED;
            case DENIED:
                return ProcessingResult.Status.DENIED;
            case REMOVED:
                return ProcessingResult.Status.REMOVED;
            case CANCELLED:
                return ProcessingResult.Status.CANCELLED;
            case NO_EXTRACTABLE_CONTENT:
            case FAILED:
            default:
                return ProcessingResult.Status.FAILED;
        }
    }
}
