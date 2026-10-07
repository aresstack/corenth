package com.aresstack.corenth.proasteion.emporion.holkas;

/**
 * Thrown by a connector when a bounded fetch finds more bytes at the source than the requested
 * limit (#10 Slice 5). The connector has stopped reading and returns no payload.
 */
public class ResourceSizeLimitExceededException extends ResourceConnectorException {

    private static final long serialVersionUID = 1L;

    private final long observedBytes;

    public ResourceSizeLimitExceededException(long observedBytes, String message) {
        super(message);
        this.observedBytes = observedBytes;
    }

    /** Return the bytes read before the read stopped; the source holds at least this many. */
    public long observedBytes() {
        return observedBytes;
    }
}
