package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

/**
 * The immutable outcome of a scope, traversal or size evaluation.
 *
 * <p>The verdict is derived from the {@link ScopeReasonCode}; the explanation is free text for
 * diagnostics only and must not be parsed.
 */
public final class ScopeDecision {

    private final ScopeReasonCode reasonCode;
    private final String explanation;

    public ScopeDecision(ScopeReasonCode reasonCode, String explanation) {
        if (reasonCode == null) {
            throw new IllegalArgumentException("reasonCode must not be null");
        }
        if (explanation == null || explanation.isEmpty()) {
            throw new IllegalArgumentException("explanation must not be null or empty");
        }
        this.reasonCode = reasonCode;
        this.explanation = explanation;
    }

    /** Return the machine-readable reason. */
    public ScopeReasonCode reasonCode() {
        return reasonCode;
    }

    /** Return the verdict implied by the reason. */
    public ScopeVerdict verdict() {
        return reasonCode.verdict();
    }

    /** Return a human-readable explanation. */
    public String explanation() {
        return explanation;
    }

    /** Return whether the resource is admitted. */
    public boolean isAdmitted() {
        return verdict() == ScopeVerdict.ADMIT;
    }

    /** Return whether the resource is rejected. */
    public boolean isRejected() {
        return verdict() == ScopeVerdict.REJECT;
    }

    /** Return whether the decision has to be repeated once the missing fact is known. */
    public boolean isUndetermined() {
        return verdict() == ScopeVerdict.UNDETERMINED;
    }

    @Override
    public String toString() {
        return "ScopeDecision{" + reasonCode + ", " + explanation + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScopeDecision)) return false;
        ScopeDecision that = (ScopeDecision) o;
        return reasonCode == that.reasonCode && explanation.equals(that.explanation);
    }

    @Override
    public int hashCode() {
        return 31 * reasonCode.hashCode() + explanation.hashCode();
    }
}
