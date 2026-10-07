package com.aresstack.corenth.astu.acropolis.chalcotheca;

import java.io.IOException;

/**
 * Thrown by an {@link AcquisitionPort} when a bounded acquisition finds more bytes at the source
 * than the requested limit (#10 Slice 5).
 *
 * <p>The port stops reading after at most one byte beyond the limit and returns no payload, so
 * an oversized resource is never acquired completely.
 */
public class AcquisitionLimitExceededException extends IOException {

    private static final long serialVersionUID = 1L;

    private final long observedBytes;

    /**
     * @param observedBytes the bytes read before the read stopped; more than the limit
     * @param message       the explanation
     */
    public AcquisitionLimitExceededException(long observedBytes, String message) {
        super(message);
        this.observedBytes = observedBytes;
    }

    /** Return the bytes read before the read stopped; the source holds at least this many. */
    public long observedBytes() {
        return observedBytes;
    }
}
