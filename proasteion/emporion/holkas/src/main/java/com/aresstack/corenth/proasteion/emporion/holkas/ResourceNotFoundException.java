package com.aresstack.corenth.proasteion.emporion.holkas;

/**
 * Thrown by a connector when the source confirms that a resource does not exist.
 *
 * <p>Connection, permission and protocol errors are other {@link ResourceConnectorException}s;
 * only a confirmed absence uses this type, because the lifecycle records it as removal at the
 * source (#10 Slice 5).
 */
public class ResourceNotFoundException extends ResourceConnectorException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
