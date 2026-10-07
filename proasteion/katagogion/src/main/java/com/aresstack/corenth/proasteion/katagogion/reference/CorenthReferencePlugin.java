package com.aresstack.corenth.proasteion.katagogion.reference;

import com.aresstack.corenth.proasteion.katagogion.CorenthPlugin;
import com.aresstack.corenth.proasteion.katagogion.PluginDescriptor;
import com.aresstack.corenth.proasteion.katagogion.Tool;
import com.aresstack.corenth.proasteion.katagogion.ToolCapability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Built-in reference plugin offering {@link LexicalSearchTool} and {@link ReadResourceTool}.
 *
 * <p>It is not registered for {@code ServiceLoader} discovery; a host installs it explicitly.
 * {@link #lexicalSearchOnly()} serves hosts that do not offer mediated reading yet.
 */
public final class CorenthReferencePlugin implements CorenthPlugin {

    /** Id of the reference plugin. */
    public static final String ID = "corenth.reference";
    private static final String VERSION = "0.1.0";

    private final PluginDescriptor descriptor;
    private final List<Tool> tools;

    private CorenthReferencePlugin(Set<ToolCapability> capabilities) {
        List<Tool> selected = new ArrayList<Tool>();
        if (capabilities.contains(ToolCapability.LEXICAL_SEARCH)) {
            selected.add(new LexicalSearchTool());
        }
        if (capabilities.contains(ToolCapability.MEDIATED_READING)) {
            selected.add(new ReadResourceTool());
        }
        this.tools = Collections.unmodifiableList(selected);
        this.descriptor = new PluginDescriptor(ID, VERSION, "Corenth reference tools", capabilities);
    }

    /** Create the plugin with both reference tools. */
    public static CorenthReferencePlugin all() {
        return new CorenthReferencePlugin(EnumSet.of(ToolCapability.LEXICAL_SEARCH, ToolCapability.MEDIATED_READING));
    }

    /** Create the plugin with the search tool only. */
    public static CorenthReferencePlugin lexicalSearchOnly() {
        return new CorenthReferencePlugin(EnumSet.of(ToolCapability.LEXICAL_SEARCH));
    }

    @Override
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public List<Tool> tools() {
        return tools;
    }
}
