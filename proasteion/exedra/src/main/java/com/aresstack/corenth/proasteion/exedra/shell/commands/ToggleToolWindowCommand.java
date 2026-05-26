package com.aresstack.corenth.proasteion.exedra.shell.commands;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;
import com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowDescriptor;
import com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowRegistry;

/**
 * Toggles visibility of a specific tool window. Command id follows the pattern
 * {@code "view.tool.<toolId>"}.
 */
public final class ToggleToolWindowCommand implements ShellCommand {

    private final String toolId;
    private final String label;
    private final ToolWindowRegistry registry;

    public ToggleToolWindowCommand(ToolWindowDescriptor descriptor, ToolWindowRegistry registry) {
        this.toolId = descriptor.getId();
        this.label = descriptor.getTitle();
        this.registry = registry;
    }

    @Override public String getId() { return "view.tool." + toolId; }
    @Override public String getLabel() { return label; }

    @Override
    public void perform() {
        boolean current = registry.isVisible(toolId);
        registry.setVisible(toolId, !current);
    }
}
