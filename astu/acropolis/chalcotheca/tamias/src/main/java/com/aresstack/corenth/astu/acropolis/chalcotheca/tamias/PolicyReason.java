package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

/**
 * Explains why a policy decision was made.
 */
public final class PolicyReason {

    private final AcceptanceDecision decision;
    private final String reason;

    public PolicyReason(AcceptanceDecision decision, String reason) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be null or empty");
        }
        this.decision = decision;
        this.reason = reason;
    }

    /** Returns the acceptance decision. */
    public AcceptanceDecision decision() {
        return decision;
    }

    /** Returns a human-readable reason for the decision. */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return decision + ": " + reason;
    }
}
