package com.aresstack.corenth.proasteion.katagogion.discovery;

import com.aresstack.corenth.proasteion.katagogion.CorenthPlugin;
import com.aresstack.corenth.proasteion.katagogion.PluginDescriptor;
import com.aresstack.corenth.proasteion.katagogion.Tool;
import com.aresstack.corenth.proasteion.katagogion.ToolContext;
import com.aresstack.corenth.proasteion.katagogion.ToolDescriptor;
import com.aresstack.corenth.proasteion.katagogion.ToolInvocation;
import com.aresstack.corenth.proasteion.katagogion.ToolResult;

import java.util.Collections;
import java.util.List;

/** Plugins declared in the test {@code META-INF/services} file. */
public final class DiscoveryTestPlugins {

    private DiscoveryTestPlugins() {
    }

    /** Valid plugin contributing one tool. */
    public static final class Beta implements CorenthPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor("test.beta", "1.0", "Beta", null);
        }

        @Override
        public List<Tool> tools() {
            return Collections.<Tool>singletonList(new Tool() {
                @Override
                public ToolDescriptor descriptor() {
                    return new ToolDescriptor("test.beta.ping", "Reply pong.", null, null);
                }

                @Override
                public ToolResult execute(ToolInvocation invocation, ToolContext context) {
                    return ToolResult.success("pong");
                }
            });
        }
    }

    /** Valid plugin without tools, sorted before {@link Beta}. */
    public static final class Alpha implements CorenthPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor("test.alpha", "1.0", "Alpha", null);
        }

        @Override
        public List<Tool> tools() {
            return Collections.emptyList();
        }
    }

    /** Second provider claiming the id of {@link Alpha}. */
    public static final class AlphaImpostor implements CorenthPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor("test.alpha", "9.9", "Impostor", null);
        }

        @Override
        public List<Tool> tools() {
            return Collections.emptyList();
        }
    }

    /** Provider whose descriptor fails. */
    public static final class Broken implements CorenthPlugin {
        @Override
        public PluginDescriptor descriptor() {
            throw new IllegalStateException("broken");
        }

        @Override
        public List<Tool> tools() {
            return Collections.emptyList();
        }
    }
}
