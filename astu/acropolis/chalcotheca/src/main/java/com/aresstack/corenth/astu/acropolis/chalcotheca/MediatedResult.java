package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;

/**
 * Result of a mediated resource operation through the bronze archive counter.
 *
 * <p>A result is either a success with payload, a withheld payload that carries the Tamias
 * decision, or a failure without decision. Failures are typed by {@link Failure} so that
 * cancellation and authentication failure stay distinguishable from acquisition errors and from
 * policy denial (#10 Slice 3).
 *
 * @param <T> the type of the result payload (BronzeListing, BronzeContent, etc.)
 */
public final class MediatedResult<T> {

    /** Kind of a failed mediated operation that carries no Tamias decision. */
    public enum Failure {
        /** The connector or the acquisition itself failed. */
        ACQUISITION_FAILED,
        /** Authentication is required but no credential is available. */
        AUTHENTICATION_UNAVAILABLE,
        /** The user cancelled the credential request. */
        AUTHENTICATION_CANCELLED,
        /** The owner of the secret refused to release it for this request (#43). */
        AUTHENTICATION_DENIED,
        /** Authentication was attempted and failed. */
        AUTHENTICATION_FAILED,
        /** The source confirmed that the resource does not exist (#10 Slice 5). */
        SOURCE_ABSENT,
        /** The source offers no metadata for the resource (#10 Slice 5). */
        METADATA_UNAVAILABLE
    }

    private final T value;
    private final ResourceAccessDecision decision;
    private final String errorMessage;
    private final boolean success;
    private final Failure failure;

    private MediatedResult(T value, ResourceAccessDecision decision, String errorMessage,
                           boolean success, Failure failure) {
        this.value = value;
        this.decision = decision;
        this.errorMessage = errorMessage;
        this.success = success;
        this.failure = failure;
    }

    public static <T> MediatedResult<T> success(T value, ResourceAccessDecision decision) {
        return new MediatedResult<T>(value, decision, null, true, null);
    }

    public static <T> MediatedResult<T> denied(ResourceAccessDecision decision) {
        return new MediatedResult<T>(null, decision, null, false, null);
    }

    /** Creates an acquisition failure ({@link Failure#ACQUISITION_FAILED}). */
    public static <T> MediatedResult<T> error(String message) {
        return failure(Failure.ACQUISITION_FAILED, message);
    }

    /** Creates a typed failure without Tamias decision. */
    public static <T> MediatedResult<T> failure(Failure failure, String message) {
        if (failure == null) throw new IllegalArgumentException("failure must not be null");
        return new MediatedResult<T>(null, null, message, false, failure);
    }

    public boolean isSuccess() { return success; }
    public boolean isDenied() { return !success && decision != null && !decision.isAllowed(); }
    public T value() { return value; }
    public ResourceAccessDecision decision() { return decision; }
    public String errorMessage() { return errorMessage; }

    /** Returns the failure kind, or {@code null} for successes and withheld results. */
    public Failure failure() { return failure; }

    @Override
    public String toString() {
        if (success) return "MediatedResult{SUCCESS, " + value + "}";
        if (decision != null) return "MediatedResult{DENIED, " + decision + "}";
        return "MediatedResult{" + failure + ", " + errorMessage + "}";
    }
}
