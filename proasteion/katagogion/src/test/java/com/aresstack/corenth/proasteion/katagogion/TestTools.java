package com.aresstack.corenth.proasteion.katagogion;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Small hand-written plugins and tools for host tests. */
final class TestTools {

    private TestTools() {
    }

    static ToolDescriptor descriptor(String name, ToolCapability... required) {
        Set<ToolCapability> capabilities = required.length == 0
                ? EnumSet.noneOf(ToolCapability.class)
                : EnumSet.copyOf(Arrays.asList(required));
        return new ToolDescriptor(name, "test tool " + name,
                Arrays.asList(ToolParameter.required("text", "input"), ToolParameter.optional("mode", "mode")),
                capabilities);
    }

    /** Echo the text argument. */
    static Tool echo(final String name, final ToolCapability... required) {
        final ToolDescriptor descriptor = descriptor(name, required);
        return new Tool() {
            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public ToolResult execute(ToolInvocation invocation, ToolContext context) {
                return ToolResult.success(invocation.argument("text"));
            }
        };
    }

    /** Call the lexical search capability regardless of grants. */
    static Tool sneakySearch(final String name) {
        final ToolDescriptor descriptor = descriptor(name);
        return new Tool() {
            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public ToolResult execute(ToolInvocation invocation, ToolContext context) {
                SearchOutcome outcome = context.lexicalSearch().search(invocation.argument("text"), 5);
                return ToolResult.success(String.valueOf(outcome.hits().size()));
            }
        };
    }

    /** Throw a runtime exception carrying a message that must not be reported. */
    static Tool failing(final String name) {
        final ToolDescriptor descriptor = descriptor(name);
        return new Tool() {
            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public ToolResult execute(ToolInvocation invocation, ToolContext context) {
                throw new IllegalStateException("internal detail token=abc123");
            }
        };
    }

    /** Return no result at all. */
    static Tool silent(final String name) {
        final ToolDescriptor descriptor = descriptor(name);
        return new Tool() {
            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public ToolResult execute(ToolInvocation invocation, ToolContext context) {
                return null;
            }
        };
    }

    static CorenthPlugin plugin(String id, Set<ToolCapability> requested, Tool... tools) {
        final PluginDescriptor descriptor = new PluginDescriptor(id, "1.0", null, requested);
        final List<Tool> toolList = Collections.unmodifiableList(Arrays.asList(tools));
        return new CorenthPlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public List<Tool> tools() {
                return toolList;
            }
        };
    }

    static Set<ToolCapability> none() {
        return EnumSet.noneOf(ToolCapability.class);
    }

    static LexicalSearch fixedSearch(final SearchOutcome outcome) {
        return new LexicalSearch() {
            @Override
            public SearchOutcome search(String queryText, int maxHits) {
                return outcome;
            }
        };
    }
}
