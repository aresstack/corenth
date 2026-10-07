package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

/**
 * The immutable outcome of change detection, together with the facts it was derived from so that
 * later decisions (derivative disposition) cannot be fed with mismatching inputs.
 *
 * <p>Instances are created by a {@link ChangeDetectionStrategy}. A decision has no side effects
 * and is not persisted: recording a new version or a removal remains Chalcotheca's job, executed
 * by the orchestration (#10).
 */
public final class ChangeDecision {

    private final ChangeReasonCode reasonCode;
    private final ResourceRecordFacts record;
    private final SourceObservation observation;

    /**
     * @param reasonCode  the machine-readable reason, implying the change kind
     * @param record      the record facts compared against, or {@code null} if no record exists
     * @param observation the current observation
     */
    public ChangeDecision(ChangeReasonCode reasonCode, ResourceRecordFacts record, SourceObservation observation) {
        if (reasonCode == null) {
            throw new IllegalArgumentException("reasonCode must not be null");
        }
        if (observation == null) {
            throw new IllegalArgumentException("observation must not be null");
        }
        ChangeKind kind = reasonCode.kind();
        boolean needsRecord = kind != ChangeKind.NEW && kind != ChangeKind.NOT_FOUND;
        if (needsRecord != (record != null)) {
            throw new IllegalArgumentException(reasonCode + (needsRecord ? " requires" : " forbids") + " record facts");
        }
        boolean needsPresence = kind == ChangeKind.NEW || kind == ChangeKind.UNCHANGED || kind == ChangeKind.CHANGED;
        if (needsPresence != observation.isPresent()) {
            throw new IllegalArgumentException(reasonCode + (needsPresence ? " requires a present" : " requires an absent")
                    + " observation");
        }
        this.reasonCode = reasonCode;
        this.record = record;
        this.observation = observation;
    }

    /** Return the machine-readable reason. */
    public ChangeReasonCode reasonCode() {
        return reasonCode;
    }

    /** Return the change kind implied by the reason. */
    public ChangeKind kind() {
        return reasonCode.kind();
    }

    /** Return the record facts compared against, or {@code null} if no record existed. */
    public ResourceRecordFacts record() {
        return record;
    }

    /** Return the observation the decision was made for. */
    public SourceObservation observation() {
        return observation;
    }

    @Override
    public String toString() {
        return "ChangeDecision{" + reasonCode + ", record=" + record + ", " + observation + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChangeDecision)) return false;
        ChangeDecision that = (ChangeDecision) o;
        return reasonCode == that.reasonCode
                && (record == null ? that.record == null : record.equals(that.record))
                && observation.equals(that.observation);
    }

    @Override
    public int hashCode() {
        int result = reasonCode.hashCode();
        result = 31 * result + (record == null ? 0 : record.hashCode());
        return 31 * result + observation.hashCode();
    }
}
