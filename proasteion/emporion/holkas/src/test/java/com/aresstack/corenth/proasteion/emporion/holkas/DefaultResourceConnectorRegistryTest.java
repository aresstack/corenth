package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceRef;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DefaultResourceConnectorRegistryTest {

    @Test
    public void find_returnsConnectorByScheme() {
        StubConnector connector = new StubConnector(ResourceScheme.FILE);
        DefaultResourceConnectorRegistry registry = DefaultResourceConnectorRegistry.of(connector);

        assertSame(connector, registry.find(ResourceScheme.FILE));
        assertTrue(registry.supportedSchemes().contains(ResourceScheme.FILE));
    }

    @Test
    public void find_returnsNullForUnknownScheme() {
        DefaultResourceConnectorRegistry registry = DefaultResourceConnectorRegistry.of(
                new StubConnector(ResourceScheme.FILE));

        assertNull(registry.find(ResourceScheme.FTP));
    }

    @Test
    public void require_failsForUnknownScheme() {
        DefaultResourceConnectorRegistry registry = DefaultResourceConnectorRegistry.of(
                new StubConnector(ResourceScheme.FILE));

        try {
            registry.require(ResourceScheme.FTP);
        } catch (ResourceConnectorException e) {
            assertTrue(e.getMessage().contains("ftp"));
            return;
        }
        throw new AssertionError("Expected ResourceConnectorException");
    }

    @Test
    public void constructor_rejectsDuplicateSchemes() {
        try {
            new DefaultResourceConnectorRegistry(Arrays.<ResourceConnector>asList(
                    new StubConnector(ResourceScheme.FILE),
                    new StubConnector(ResourceScheme.FILE)));
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("duplicate"));
            return;
        }
        throw new AssertionError("Expected duplicate connector rejection");
    }

    @Test
    public void constructor_acceptsEmptyList() {
        DefaultResourceConnectorRegistry registry = new DefaultResourceConnectorRegistry(
                Collections.<ResourceConnector>emptyList());

        assertEquals(0, registry.supportedSchemes().size());
    }

    private static final class StubConnector implements ResourceConnector {
        private final ResourceScheme scheme;

        private StubConnector(ResourceScheme scheme) {
            this.scheme = scheme;
        }

        @Override
        public ResourceScheme supportedScheme() {
            return scheme;
        }

        @Override
        public RawResource fetch(VirtualResourceRef ref) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceListing list(VirtualResourceRef ref) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
