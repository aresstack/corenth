package com.aresstack.corenth.astu.acropolis.chalcotheca;

import java.io.IOException;

/**
 * Thrown by an {@link AcquisitionPort} when the source confirms that the resource does not exist.
 *
 * <p>Only a confirmed absence qualifies, for example a missing local file. Connection errors,
 * timeouts, denials and authentication failures are other failures; they never count as removal
 * at the source (#10 Slice 5).
 */
public class SourceAbsentException extends IOException {

    private static final long serialVersionUID = 1L;

    public SourceAbsentException(String message) {
        super(message);
    }

    public SourceAbsentException(String message, Throwable cause) {
        super(message, cause);
    }
}
