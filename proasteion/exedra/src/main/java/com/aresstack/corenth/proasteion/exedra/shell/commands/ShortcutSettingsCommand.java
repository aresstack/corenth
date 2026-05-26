package com.aresstack.corenth.proasteion.exedra.shell.commands;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;
import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.ShortcutRegistry;

import javax.swing.JFrame;

/**
 * Opens the keyboard shortcut editor dialog. Registered as {@code "tools.shortcuts"}.
 * After editing, re-applies bindings to the root pane.
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
        ShortcutEditorDialog dialog = new ShortcutEditorDialog(frame, shortcutRegistry, commandRegistry);
        dialog.setVisible(true);
        if (dialog.wasApplied()) {
            // Re-apply shortcuts to root pane after editing
            shortcutRegistry.applyToRootPane(frame.getRootPane(), commandRegistry);
        }
    }
}
