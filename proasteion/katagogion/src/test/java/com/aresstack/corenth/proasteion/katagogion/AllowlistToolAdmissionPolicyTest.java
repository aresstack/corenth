package com.aresstack.corenth.proasteion.katagogion;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowlistToolAdmissionPolicyTest {

    private final AllowlistToolAdmissionPolicy policy = AllowlistToolAdmissionPolicy.builder()
            .allow("search.plugin", EnumSet.of(ToolCapability.LEXICAL_SEARCH))
            .allow("plain.plugin", null)
            .build();

    @Test
    void unlistedPluginIsRejected() {
        ToolAdmission admission = policy.evaluate(new PluginDescriptor("other.plugin", "1", null, null));
        assertFalse(admission.isAdmitted());
        assertTrue(admission.reason().contains("not on the tool allowlist"));
        assertTrue(admission.grantedCapabilities().isEmpty());
    }

    @Test
    void requestWithinAllowanceIsGrantedExactly() {
        ToolAdmission admission = policy.evaluate(new PluginDescriptor("search.plugin", "1", null,
                EnumSet.of(ToolCapability.LEXICAL_SEARCH)));
        assertTrue(admission.isAdmitted());
        assertEquals(EnumSet.of(ToolCapability.LEXICAL_SEARCH), admission.grantedCapabilities());
    }

    @Test
    void requestBeyondAllowanceIsRejectedInsteadOfDowngraded() {
        ToolAdmission admission = policy.evaluate(new PluginDescriptor("search.plugin", "1", null,
                EnumSet.of(ToolCapability.LEXICAL_SEARCH, ToolCapability.MEDIATED_READING)));
        assertFalse(admission.isAdmitted());
        assertTrue(admission.reason().contains("MEDIATED_READING"));
    }

    @Test
    void pluginWithoutCapabilitiesCanBeAdmittedWithoutGrants() {
        ToolAdmission admission = policy.evaluate(new PluginDescriptor("plain.plugin", "1", null, null));
        assertTrue(admission.isAdmitted());
        assertTrue(admission.grantedCapabilities().isEmpty());
        assertFalse(policy.evaluate(new PluginDescriptor("plain.plugin", "1", null,
                EnumSet.of(ToolCapability.LEXICAL_SEARCH))).isAdmitted());
    }
}
