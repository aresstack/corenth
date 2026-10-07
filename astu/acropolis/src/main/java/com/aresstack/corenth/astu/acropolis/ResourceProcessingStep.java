package com.aresstack.corenth.astu.acropolis;

/**
 * One executed lifecycle step: its type, how it ended and a diagnostic detail.
 *
 * <p>Immutable. The detail never contains payloads or secret material.
 */
public final class ResourceProcessingStep {

    /** How a step ended. */
    public enum Status {
        /** The step finished and the lifecycle continued. */
        COMPLETED,
        /** The step finished with a decision that ends the lifecycle for this resource. */
        STOPPED,
        /** The step could not be executed. */
        FAILED
    }

    private final ResourceProcessingStepType type;
    private final Status status;
    private final String detail;

    public ResourceProcessingStep(ResourceProcessingStepType type, Status status, String detail) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        this.type = type;
        this.status = status;
        this.detail = detail;
    }

    public static ResourceProcessingStep completed(ResourceProcessingStepType type, String detail) {
        return new ResourceProcessingStep(type, Status.COMPLETED, detail);
    }

    public static ResourceProcessingStep stopped(ResourceProcessingStepType type, String detail) {
        return new ResourceProcessingStep(type, Status.STOPPED, detail);
    }

    public static ResourceProcessingStep failed(ResourceProcessingStepType type, String detail) {
        return new ResourceProcessingStep(type, Status.FAILED, detail);
    }

    public ResourceProcessingStepType type() { return type; }

    public Status status() { return status; }

    /** Returns a diagnostic detail; may be {@code null}. */
    public String detail() { return detail; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceProcessingStep)) return false;
        ResourceProcessingStep that = (ResourceProcessingStep) o;
        return type == that.type && status == that.status
                && (detail == null ? that.detail == null : detail.equals(that.detail));
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + status.hashCode();
        return 31 * result + (detail == null ? 0 : detail.hashCode());
    }

    @Override
    public String toString() {
        return type + ":" + status + (detail == null ? "" : "(" + detail + ")");
    }
}
