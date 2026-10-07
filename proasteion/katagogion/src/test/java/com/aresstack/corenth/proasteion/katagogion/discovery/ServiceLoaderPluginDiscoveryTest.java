package com.aresstack.corenth.proasteion.katagogion.discovery;

import com.aresstack.corenth.proasteion.katagogion.AllowlistToolAdmissionPolicy;
import com.aresstack.corenth.proasteion.katagogion.CorenthPlugin;
import com.aresstack.corenth.proasteion.katagogion.PluginInstallation;
import com.aresstack.corenth.proasteion.katagogion.PluginRejectionReason;
import com.aresstack.corenth.proasteion.katagogion.ToolCapabilities;
import com.aresstack.corenth.proasteion.katagogion.ToolHost;
import com.aresstack.corenth.proasteion.katagogion.ToolInvocation;
import com.aresstack.corenth.proasteion.katagogion.ToolResult;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceLoaderPluginDiscoveryTest {

    private final DiscoveryReport report =
            new ServiceLoaderPluginDiscovery(getClass().getClassLoader()).discover();

    @Test
    void discoversValidProvidersOrderedById() {
        List<CorenthPlugin> plugins = report.plugins();

        assertEquals(2, plugins.size());
        assertEquals("test.alpha", plugins.get(0).descriptor().id());
        assertEquals("1.0", plugins.get(0).descriptor().version());
        assertEquals("test.beta", plugins.get(1).descriptor().id());
    }

    @Test
    void reportsBrokenMissingAndDuplicateProvidersWithoutAborting() {
        List<String> problems = report.problems();

        assertEquals(3, problems.size(), problems.toString());
        assertTrue(contains(problems, "MissingPlugin"), problems.toString());
        assertTrue(contains(problems, "Broken"), problems.toString());
        assertTrue(contains(problems, "duplicate plugin id 'test.alpha'"), problems.toString());
    }

    @Test
    void discoveryDoesNotInstallAndInstallationStaysSubjectToAdmission() {
        ToolHost host = new ToolHost(AllowlistToolAdmissionPolicy.builder()
                .allow("test.beta", null)
                .build(), ToolCapabilities.none());
        assertTrue(host.tools().isEmpty());

        PluginInstallation alpha = host.install(report.plugins().get(0));
        PluginInstallation beta = host.install(report.plugins().get(1));

        assertEquals(PluginRejectionReason.NOT_ADMITTED, alpha.rejectionReason());
        assertTrue(beta.isInstalled());
        ToolResult pong = host.invoke(new ToolInvocation("test.beta.ping", Collections.<String, String>emptyMap()));
        assertEquals("pong", pong.text());
    }

    @Test
    void classLoaderWithoutProvidersYieldsAnEmptyReport() {
        DiscoveryReport empty = new ServiceLoaderPluginDiscovery(new URLClassLoader(new URL[0], null)).discover();
        assertTrue(empty.plugins().isEmpty());
        assertTrue(empty.problems().isEmpty());
    }

    private static boolean contains(List<String> problems, String fragment) {
        for (String problem : problems) {
            if (problem.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
