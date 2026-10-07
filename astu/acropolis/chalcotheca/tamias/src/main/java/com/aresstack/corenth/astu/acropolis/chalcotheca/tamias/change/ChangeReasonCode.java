package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

/**
 * Stable, machine-readable reasons for a {@link ChangeDecision}. Each code implies exactly one
 * {@link ChangeKind}; the finer codes keep facts that the kind alone would lose (for example a
 * resource reappearing after a tombstone).
 */
public enum ChangeReasonCode {
    /** No record exists; the resource is seen for the first time. */
    FIRST_SEEN(ChangeKind.NEW),
    /** The observed digest equals the latest observed version. */
    DIGEST_UNCHANGED(ChangeKind.UNCHANGED),
    /** The resource was recorded as removed and reappeared with the latest observed digest. */
    REAPPEARED_UNCHANGED(ChangeKind.UNCHANGED),
    /** The observed digest differs from the latest observed version. */
    DIGEST_CHANGED(ChangeKind.CHANGED),
    /** The resource was recorded as removed and reappeared with a different digest. */
    REAPPEARED_CHANGED(ChangeKind.CHANGED),
    /** The resource is absent at the source and no removal was recorded yet. */
    REMOVAL_OBSERVED(ChangeKind.REMOVED),
    /** The resource is absent at the source and the removal is already recorded. */
    REMOVAL_ALREADY_RECORDED(ChangeKind.REMOVED),
    /** The resource is neither recorded nor present at the source. */
    NOT_FOUND_AT_SOURCE(ChangeKind.NOT_FOUND);

    private final ChangeKind kind;

    ChangeReasonCode(ChangeKind kind) {
        this.kind = kind;
    }

    /** Return the change kind implied by this reason. */
    public ChangeKind kind() {
        return kind;
    }
}
