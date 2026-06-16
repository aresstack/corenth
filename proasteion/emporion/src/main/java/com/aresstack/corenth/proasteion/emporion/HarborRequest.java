package com.aresstack.corenth.proasteion.emporion;

import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceReadMode;

/**
 * Request for a harbor-level resource operation.
 */
public final class HarborRequest {

    private final VirtualResourceRef resourceRef;
    private final ResourceReadMode readMode;

    public HarborRequest(VirtualResourceRef resourceRef) {
        this(resourceRef, ResourceReadMode.DEFAULT);
    }

    public HarborRequest(VirtualResourceRef resourceRef, ResourceReadMode readMode) {
        if (resourceRef == null) {
            throw new IllegalArgumentException("resourceRef must not be null");
        }
        this.resourceRef = resourceRef;
        this.readMode = readMode != null ? readMode : ResourceReadMode.DEFAULT;
    }

    public VirtualResourceRef resourceRef() {
        return resourceRef;
    }

    public ResourceReadMode readMode() {
        return readMode;
    }
}
