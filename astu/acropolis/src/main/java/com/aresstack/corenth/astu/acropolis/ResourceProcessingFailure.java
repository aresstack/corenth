package com.aresstack.corenth.astu.acropolis;

/**
 * Machine-readable reason why processing a resource did not complete (#10 Slice 4).
 *
 * <p>Immutable. The message is diagnostic and never contains payloads, secret material or
 * authorisation data.
 */
public final class ResourceProcessingFailure {

    /** Reason code of a failure, cancellation or empty extraction. */
    public enum Reason {
        INVALID_REQUEST,
        MEDIATED_ACCESS_ERROR,
        ACQUISITION_FAILED,
        AUTHENTICATION_UNAVAILABLE,
        AUTHENTICATION_CANCELLED,
        AUTHENTICATION_FAILED,
        SOURCE_NOT_FOUND,
        INSPECTION_FAILED,
        NO_INDEXABLE_TEXT,
        INDEXING_FAILED,
        CLEANUP_FAILED
    }

    private final Reason reason;
    private final String message;

    public ResourceProcessingFailure(Reason reason, String message) {
        if (reason == null) throw new IllegalArgumentException("reason must not be null");
        this.reason = reason;
        this.message = message;
    }

    public Reason reason() { return reason; }

    /** Returns a diagnostic message; may be {@code null}. */
    public String message() { return message; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceProcessingFailure)) return false;
        ResourceProcessingFailure that = (ResourceProcessingFailure) o;
        return reason == that.reason
                && (message == null ? that.message == null : message.equals(that.message));
    }

    @Override
    public int hashCode() {
        return 31 * reason.hashCode() + (message == null ? 0 : message.hashCode());
    }

    @Override
    public String toString() {
        return reason + (message == null ? "" : ": " + message);
    }
}
