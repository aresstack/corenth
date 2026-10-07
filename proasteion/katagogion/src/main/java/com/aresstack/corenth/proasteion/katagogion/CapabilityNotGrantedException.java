package com.aresstack.corenth.proasteion.katagogion;

/** Signal that a tool used a capability its plugin was not granted. */
public final class CapabilityNotGrantedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ToolCapability capability;

    public CapabilityNotGrantedException(ToolCapability capability) {
        super("capability not granted: " + capability);
        this.capability = capability;
    }

    public ToolCapability capability() {
        return capability;
    }
}
