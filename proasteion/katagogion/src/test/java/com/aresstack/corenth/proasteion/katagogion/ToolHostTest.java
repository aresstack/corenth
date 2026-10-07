package com.aresstack.corenth.proasteion.katagogion;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.aresstack.corenth.proasteion.katagogion.TestTools.echo;
import static com.aresstack.corenth.proasteion.katagogion.TestTools.none;
import static com.aresstack.corenth.proasteion.katagogion.TestTools.plugin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolHostTest {

    private static final EnumSet<ToolCapability> SEARCH = EnumSet.of(ToolCapability.LEXICAL_SEARCH);

    private final ToolCapabilities searchOnly = ToolCapabilities.builder()
            .lexicalSearch(TestTools.fixedSearch(SearchOutcome.hits(null)))
            .build();

    private final ToolAdmissionPolicy admitAll = new ToolAdmissionPolicy() {
        @Override
        public ToolAdmission evaluate(PluginDescriptor plugin) {
            return ToolAdmission.admit(plugin.requestedCapabilities(), "test");
        }
    };

    private ToolHost host() {
        return new ToolHost(admitAll, searchOnly);
    }

    private static ToolInvocation call(String tool, String... keyValues) {
        Map<String, String> arguments = new LinkedHashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            arguments.put(keyValues[i], keyValues[i + 1]);
        }
        return new ToolInvocation(tool, arguments);
    }

    @Test
    void installsPluginExplicitlyAndListsToolsSortedByName() {
        ToolHost host = host();

        PluginInstallation installation = host.install(plugin("demo", none(), echo("zeta"), echo("alpha")));

        assertTrue(installation.isInstalled());
        assertEquals("demo", installation.pluginId());
        assertEquals(java.util.Arrays.asList("zeta", "alpha"), installation.installedTools());
        List<ToolListing> tools = host.tools();
        assertEquals(2, tools.size());
        assertEquals("alpha", tools.get(0).descriptor().name());
        assertEquals("demo", tools.get(0).pluginId());
        assertEquals("zeta", tools.get(1).descriptor().name());
    }

    @Test
    void invokesInstalledTool() {
        ToolHost host = host();
        host.install(plugin("demo", none(), echo("echo")));

        ToolResult result = host.invoke(call("echo", "text", "hello"));

        assertTrue(result.isSuccess());
        assertEquals("hello", result.text());
    }

    @Test
    void duplicateToolNameRejectsWholePluginAtomically() {
        ToolHost host = host();
        host.install(plugin("first", none(), echo("shared")));

        PluginInstallation second = host.install(plugin("second", none(), echo("unique"), echo("shared")));

        assertFalse(second.isInstalled());
        assertEquals(PluginRejectionReason.DUPLICATE_TOOL, second.rejectionReason());
        assertEquals(1, host.tools().size());
        assertEquals(ToolFailureReason.UNKNOWN_TOOL, host.invoke(call("unique", "text", "x")).failureReason());
        assertEquals("first", host.tools().get(0).pluginId());
    }

    @Test
    void duplicateToolNameWithinOnePluginIsRejected() {
        PluginInstallation installation = host().install(plugin("demo", none(), echo("same"), echo("same")));
        assertEquals(PluginRejectionReason.DUPLICATE_TOOL, installation.rejectionReason());
    }

    @Test
    void samePluginIdCannotBeInstalledTwice() {
        ToolHost host = host();
        host.install(plugin("demo", none(), echo("one")));

        PluginInstallation again = host.install(plugin("demo", none(), echo("two")));

        assertEquals(PluginRejectionReason.DUPLICATE_PLUGIN, again.rejectionReason());
        assertEquals(1, host.tools().size());
    }

    @Test
    void admissionRejectionPreventsRegistration() {
        ToolHost host = new ToolHost(AllowlistToolAdmissionPolicy.builder().build(), searchOnly);

        PluginInstallation installation = host.install(plugin("stranger", none(), echo("tool")));

        assertEquals(PluginRejectionReason.NOT_ADMITTED, installation.rejectionReason());
        assertTrue(installation.message().contains("allowlist"));
        assertTrue(installation.installedTools().isEmpty());
        assertTrue(host.tools().isEmpty());
    }

    @Test
    void toolRequiringUnrequestedCapabilityIsRejected() {
        PluginInstallation installation = host().install(
                plugin("demo", none(), echo("search", ToolCapability.LEXICAL_SEARCH)));
        assertEquals(PluginRejectionReason.CAPABILITY_NOT_GRANTED, installation.rejectionReason());
    }

    @Test
    void grantsNarrowerThanRequestRejectToolsThatNeedTheMissingCapability() {
        ToolAdmissionPolicy grantNothing = new ToolAdmissionPolicy() {
            @Override
            public ToolAdmission evaluate(PluginDescriptor plugin) {
                return ToolAdmission.admit(none(), "admitted without grants");
            }
        };
        ToolHost host = new ToolHost(grantNothing, searchOnly);

        PluginInstallation installation = host.install(
                plugin("demo", SEARCH, echo("search", ToolCapability.LEXICAL_SEARCH)));

        assertEquals(PluginRejectionReason.CAPABILITY_NOT_GRANTED, installation.rejectionReason());
    }

    @Test
    void toolRequiringCapabilityTheHostDoesNotOfferIsRejected() {
        PluginInstallation installation = host().install(plugin("demo",
                EnumSet.of(ToolCapability.MEDIATED_READING), echo("read", ToolCapability.MEDIATED_READING)));
        assertEquals(PluginRejectionReason.CAPABILITY_UNAVAILABLE, installation.rejectionReason());
    }

    @Test
    void toolUsingAnUngrantedCapabilityAtRuntimeGetsATypedFailure() {
        ToolHost host = host();
        host.install(plugin("granted", SEARCH, echo("granted-tool", ToolCapability.LEXICAL_SEARCH)));
        host.install(plugin("sneaky", none(), TestTools.sneakySearch("sneaky-tool")));

        ToolResult result = host.invoke(call("sneaky-tool", "text", "anything"));

        assertEquals(ToolFailureReason.CAPABILITY_NOT_GRANTED, result.failureReason());
    }

    @Test
    void grantedCapabilityIsUsableAtRuntime() {
        ToolHost host = host();
        host.install(plugin("demo", SEARCH, TestTools.sneakySearch("search-tool")));

        ToolResult result = host.invoke(call("search-tool", "text", "anything"));

        assertTrue(result.isSuccess());
        assertEquals("0", result.text());
    }

    @Test
    void invalidInvocationsAreRejectedBeforeTheToolRuns() {
        ToolHost host = host();
        host.install(plugin("demo", none(), echo("echo")));

        assertEquals(ToolFailureReason.UNKNOWN_TOOL, host.invoke(call("nope", "text", "x")).failureReason());
        assertEquals(ToolFailureReason.INVALID_ARGUMENTS, host.invoke(call("echo")).failureReason());
        assertEquals(ToolFailureReason.INVALID_ARGUMENTS, host.invoke(call("echo", "text", "  ")).failureReason());
        assertEquals(ToolFailureReason.INVALID_ARGUMENTS,
                host.invoke(call("echo", "text", "x", "path", "/etc/passwd")).failureReason());
        assertTrue(host.invoke(call("echo", "text", "x", "mode", "loud")).isSuccess());
        assertEquals(ToolFailureReason.INVALID_ARGUMENTS, host.invoke(null).failureReason());
    }

    @Test
    void toolExceptionsBecomeExecutionFailuresWithoutLeakingMessages() {
        ToolHost host = host();
        host.install(plugin("demo", none(), TestTools.failing("broken"), TestTools.silent("silent")));

        ToolResult broken = host.invoke(call("broken", "text", "x"));
        ToolResult silent = host.invoke(call("silent", "text", "x"));

        assertEquals(ToolFailureReason.EXECUTION_FAILED, broken.failureReason());
        assertTrue(broken.text().contains("IllegalStateException"));
        assertFalse(broken.text().contains("abc123"));
        assertEquals(ToolFailureReason.EXECUTION_FAILED, silent.failureReason());
    }

    @Test
    void brokenPluginsAreRejectedAsInvalid() {
        ToolHost host = host();
        CorenthPlugin throwingDescriptor = new CorenthPlugin() {
            @Override
            public PluginDescriptor descriptor() {
                throw new IllegalStateException("boom");
            }

            @Override
            public List<Tool> tools() {
                return Collections.emptyList();
            }
        };
        CorenthPlugin nullTools = new CorenthPlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return new PluginDescriptor("null.tools", "1", null, null);
            }

            @Override
            public List<Tool> tools() {
                return null;
            }
        };

        PluginInstallation first = host.install(throwingDescriptor);
        PluginInstallation second = host.install(nullTools);

        assertEquals(PluginRejectionReason.INVALID_PLUGIN, first.rejectionReason());
        assertNull(first.pluginId());
        assertEquals(PluginRejectionReason.INVALID_PLUGIN, second.rejectionReason());
        assertEquals(PluginRejectionReason.INVALID_PLUGIN, host.install(null).rejectionReason());
        assertTrue(host.tools().isEmpty());
    }

    @Test
    void rejectedPluginIdCanBeInstalledLaterOnceValid() {
        ToolHost host = host();
        assertFalse(host.install(plugin("demo", none(), echo("x", ToolCapability.LEXICAL_SEARCH))).isInstalled());
        assertTrue(host.install(plugin("demo", none(), echo("x"))).isInstalled());
    }
}
