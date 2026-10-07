package com.aresstack.corenth.proasteion.katagogion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolContractsTest {

    @Test
    void descriptorCopiesInputsAndIsUnmodifiable() {
        List<ToolParameter> parameters = new ArrayList<ToolParameter>();
        parameters.add(ToolParameter.required("query", "text"));
        Set<ToolCapability> capabilities = EnumSet.of(ToolCapability.LEXICAL_SEARCH);

        ToolDescriptor descriptor = new ToolDescriptor("corenth.search", null, parameters, capabilities);
        parameters.add(ToolParameter.optional("other", "x"));
        capabilities.add(ToolCapability.MEDIATED_READING);

        assertEquals(1, descriptor.parameters().size());
        assertEquals(EnumSet.of(ToolCapability.LEXICAL_SEARCH), descriptor.requiredCapabilities());
        assertEquals("", descriptor.description());
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.parameters().add(ToolParameter.optional("x", "x")));
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.requiredCapabilities().add(ToolCapability.MEDIATED_READING));
        assertNull(descriptor.parameter("missing"));
        assertTrue(descriptor.parameter("query").isRequired());
    }

    @Test
    void descriptorRejectsInvalidNamesAndDuplicateParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("Search Tool", "", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("", "", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("../evil", "", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("dup", "", Arrays.asList(
                        ToolParameter.required("a", ""), ToolParameter.optional("a", "")), null));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("nulls", "", Collections.<ToolParameter>singletonList(null), null));
    }

    @Test
    void pluginDescriptorValidatesIdAndVersion() {
        assertThrows(IllegalArgumentException.class, () -> new PluginDescriptor("Bad Id", "1", null, null));
        assertThrows(IllegalArgumentException.class, () -> new PluginDescriptor("ok", " ", null, null));
        PluginDescriptor descriptor = new PluginDescriptor("ok.plugin", "1.0", null, null);
        assertEquals("ok.plugin", descriptor.displayName());
        assertTrue(descriptor.requestedCapabilities().isEmpty());
    }

    @Test
    void invocationCopiesArgumentsAndRejectsNulls() {
        Map<String, String> arguments = new LinkedHashMap<String, String>();
        arguments.put("query", "cobol");
        ToolInvocation invocation = new ToolInvocation("corenth.search", arguments);
        arguments.put("query", "changed");

        assertEquals("cobol", invocation.argument("query"));
        assertThrows(UnsupportedOperationException.class, () -> invocation.arguments().put("x", "y"));
        Map<String, String> withNull = new HashMap<String, String>();
        withNull.put("query", null);
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocation("t", withNull));
    }

    @Test
    void resultIsTypedAndHoldsOnlyImmutableStringRecords() {
        Map<String, String> record = new LinkedHashMap<String, String>();
        record.put("uri", "file:///a.txt");
        List<Map<String, String>> records = new ArrayList<Map<String, String>>();
        records.add(record);

        ToolResult success = ToolResult.success("1 hit", records);
        record.put("uri", "changed");
        records.clear();

        assertTrue(success.isSuccess());
        assertNull(success.failureReason());
        assertEquals("file:///a.txt", success.records().get(0).get("uri"));
        assertThrows(UnsupportedOperationException.class, () -> success.records().get(0).put("x", "y"));

        ToolResult failure = ToolResult.failure(ToolFailureReason.ACCESS_DENIED, "denied");
        assertFalse(failure.isSuccess());
        assertEquals(ToolFailureReason.ACCESS_DENIED, failure.failureReason());
        assertTrue(failure.records().isEmpty());
    }

    @Test
    void contextRefusesUngrantedCapabilities() {
        ToolCapabilities capabilities = ToolCapabilities.builder()
                .lexicalSearch(TestTools.fixedSearch(SearchOutcome.hits(null)))
                .build();
        ToolContext none = ToolContext.granting(capabilities, TestTools.none());

        CapabilityNotGrantedException failure =
                assertThrows(CapabilityNotGrantedException.class, none::lexicalSearch);
        assertEquals(ToolCapability.LEXICAL_SEARCH, failure.capability());
        assertThrows(CapabilityNotGrantedException.class, ToolContext.empty()::mediatedReading);

        ToolContext granted = ToolContext.granting(capabilities, EnumSet.of(ToolCapability.LEXICAL_SEARCH));
        assertTrue(granted.lexicalSearch().search("x", 1).isAvailable());
    }

    @Test
    void contextCannotGrantAnUnimplementedCapability() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolContext.granting(ToolCapabilities.none(), EnumSet.of(ToolCapability.MEDIATED_READING)));
    }
}
