package com.aresstack.corenth.proasteion.exedra.shell.commands;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;
import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.ShortcutRegistry;

import javax.swing.JFrame;

/**
 * Opens the keyboard shortcut settings. Registered as {@code "tools.shortcuts"}.
 * The actual editor UI is left to application integrators — this command serves
 * as the registration point and performs a reload of root-pane bindings.
 */
public final class ShortcutSettingsCommand implements ShellCommand {

    private final JFrame frame;
    private final ShortcutRegistry shortcutRegistry;
    private final CommandRegistry commandRegistry;

    public ShortcutSettingsCommand(JFrame frame, ShortcutRegistry shortcutRegistry,
                                    CommandRegistry commandRegistry) {
        this.frame = frame;
        this.shortcutRegistry = shortcutRegistry;
        this.commandRegistry = commandRegistry;
    }

    @Override public String getId() { return "tools.shortcuts"; }
    @Override public String getLabel() { return "Keyboard Shortcuts..."; }

    @Override
    public void perform() {
        // Re-apply shortcuts to root pane (in case they were edited externally)
        shortcutRegistry.applyToRootPane(frame.getRootPane(), commandRegistry);
    }
}
