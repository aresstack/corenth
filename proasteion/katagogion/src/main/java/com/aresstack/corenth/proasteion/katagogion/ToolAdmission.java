package com.aresstack.corenth.proasteion.katagogion;

import java.util.Set;

/** Immutable decision of a {@link ToolAdmissionPolicy}: admitted with grants, or rejected with a reason. */
public final class ToolAdmission {

    private final boolean admitted;
    private final Set<ToolCapability> grantedCapabilities;
    private final String reason;

    private ToolAdmission(boolean admitted, Set<ToolCapability> grantedCapabilities, String reason) {
        this.admitted = admitted;
        this.grantedCapabilities = grantedCapabilities;
        this.reason = reason;
    }

    /** Admit the plugin with the given capability grants. */
    public static ToolAdmission admit(Set<ToolCapability> grantedCapabilities, String reason) {
        return new ToolAdmission(true, ToolDescriptor.copyCapabilities(grantedCapabilities), Names.nullToEmpty(reason));
    }

    /** Reject the plugin. */
    public static ToolAdmission reject(String reason) {
        return new ToolAdmission(false, ToolDescriptor.copyCapabilities(null), Names.nullToEmpty(reason));
    }

    public boolean isAdmitted() {
        return admitted;
    }

    /** Return the granted capabilities; empty for a rejection. */
    public Set<ToolCapability> grantedCapabilities() {
        return grantedCapabilities;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return admitted ? "ToolAdmission{ADMITTED, " + grantedCapabilities + "}" : "ToolAdmission{REJECTED, " + reason + "}";
    }
}
