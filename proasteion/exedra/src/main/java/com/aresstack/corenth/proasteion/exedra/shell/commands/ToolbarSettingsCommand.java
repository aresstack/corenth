package com.aresstack.corenth.proasteion.exedra.shell.commands;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;
import com.aresstack.corenth.proasteion.exedra.toolbar.ConfigurableToolbar;

/**
 * Opens the toolbar configuration dialog. Registered as {@code "tools.toolbar"}.
 */
public final class ToolbarSettingsCommand implements ShellCommand {

    private final ConfigurableToolbar toolbar;

    public ToolbarSettingsCommand(ConfigurableToolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override public String getId() { return "tools.toolbar"; }
    @Override public String getLabel() { return "Toolbar Settings..."; }

    @Override
    public void perform() {
        // Reload shows the configuration. Applications can override for a dialog.
        toolbar.reload();
    }
}
