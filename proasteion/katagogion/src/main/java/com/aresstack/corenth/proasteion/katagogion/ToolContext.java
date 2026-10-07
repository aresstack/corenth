package com.aresstack.corenth.proasteion.katagogion;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The only capabilities a tool receives: mediated views granted to its plugin.
 *
 * <p>There is no accessor for files, network, acquisition, policies, secrets, the host or other
 * plugins. Asking for a capability that was not granted throws
 * {@link CapabilityNotGrantedException}, which the host reports as
 * {@link ToolFailureReason#CAPABILITY_NOT_GRANTED}.
 *
 * <p>This is a capability boundary for cooperating code, not an isolation mechanism: plugin code
 * runs in the Corenth JVM with the same Java permissions as the host.
 */
public final class ToolContext {

    private final ToolCapabilities capabilities;
    private final Set<ToolCapability> granted;

    private ToolContext(ToolCapabilities capabilities, Set<ToolCapability> granted) {
        this.capabilities = capabilities;
        this.granted = granted;
    }

    /**
     * Create a context exposing the granted subset of the offered capabilities.
     *
     * @throws IllegalArgumentException if a granted capability has no implementation
     */
    public static ToolContext granting(ToolCapabilities capabilities, Set<ToolCapability> granted) {
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities must not be null");
        }
        Set<ToolCapability> grants = ToolDescriptor.copyCapabilities(granted);
        if (!capabilities.available().containsAll(grants)) {
            EnumSet<ToolCapability> missing = EnumSet.copyOf(grants);
            missing.removeAll(capabilities.available());
            throw new IllegalArgumentException("granted capabilities without implementation: " + missing);
        }
        return new ToolContext(capabilities, grants);
    }

    /** Return a context without any capability. */
    public static ToolContext empty() {
        return new ToolContext(ToolCapabilities.none(),
                Collections.unmodifiableSet(EnumSet.noneOf(ToolCapability.class)));
    }

    public Set<ToolCapability> grantedCapabilities() {
        return granted;
    }

    /** Return the lexical search capability. */
    public LexicalSearch lexicalSearch() {
        require(ToolCapability.LEXICAL_SEARCH);
        return capabilities.lexicalSearch();
    }

    /** Return the mediated reading capability. */
    public MediatedReading mediatedReading() {
        require(ToolCapability.MEDIATED_READING);
        return capabilities.mediatedReading();
    }

    private void require(ToolCapability capability) {
        if (!granted.contains(capability)) {
            throw new CapabilityNotGrantedException(capability);
        }
    }
}
