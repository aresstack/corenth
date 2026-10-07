package com.aresstack.corenth.astu.acropolis.chalcotheca;

/**
 * Typed result of preparing access for one external acquisition.
 *
 * <p>Exactly one status applies:
 * <ul>
 *   <li>{@link Status#NOT_REQUIRED}: the source needs no authentication; acquire without a
 *       capability. Local and unauthenticated sources end here without any vault call.</li>
 *   <li>{@link Status#GRANTED}: an authenticated {@link AcquisitionCapability} is attached.</li>
 *   <li>{@link Status#UNAVAILABLE}: authentication is required but no credential could be
 *       provided (for example no configured secret source).</li>
 *   <li>{@link Status#CANCELLED}: the user cancelled an interactive credential request.</li>
 *   <li>{@link Status#FAILED}: authentication was attempted and failed.</li>
 * </ul>
 * Access denial by policy is not a status here; it is a Tamias decision made before preparation.
 * The detail text is diagnostic and must never contain secret material.
 */
public final class AcquisitionAccess implements AutoCloseable {

    /** Outcome of the access preparation. */
    public enum Status {
        NOT_REQUIRED,
        GRANTED,
        UNAVAILABLE,
        CANCELLED,
        FAILED
    }

    private static final AcquisitionAccess NOT_REQUIRED =
            new AcquisitionAccess(Status.NOT_REQUIRED, null, "no authentication required");

    private final Status status;
    private final AcquisitionCapability capability;
    private final String detail;

    private AcquisitionAccess(Status status, AcquisitionCapability capability, String detail) {
        this.status = status;
        this.capability = capability;
        this.detail = detail;
    }

    /** The source needs no authentication. */
    public static AcquisitionAccess notRequired() {
        return NOT_REQUIRED;
    }

    /** Authentication succeeded; the counter owns and closes the capability. */
    public static AcquisitionAccess granted(AcquisitionCapability capability) {
        if (capability == null) throw new IllegalArgumentException("capability must not be null");
        return new AcquisitionAccess(Status.GRANTED, capability, "access granted");
    }

    /** Authentication is required but no credential is available. */
    public static AcquisitionAccess unavailable(String detail) {
        return new AcquisitionAccess(Status.UNAVAILABLE, null, detail);
    }

    /** The user cancelled the credential request. */
    public static AcquisitionAccess cancelled(String detail) {
        return new AcquisitionAccess(Status.CANCELLED, null, detail);
    }

    /** Authentication was attempted and failed. */
    public static AcquisitionAccess failed(String detail) {
        return new AcquisitionAccess(Status.FAILED, null, detail);
    }

    /** Returns the preparation status. */
    public Status status() { return status; }

    /** Returns the capability for {@link Status#GRANTED}, otherwise {@code null}. */
    public AcquisitionCapability capability() { return capability; }

    /** Returns a diagnostic detail without secret material; may be {@code null}. */
    public String detail() { return detail; }

    /** Closes the attached capability, if any. */
    @Override
    public void close() {
        if (capability != null) {
            capability.close();
        }
    }

    @Override
    public String toString() {
        return "AcquisitionAccess{" + status + (detail == null ? "" : ", " + detail) + "}";
    }
}
