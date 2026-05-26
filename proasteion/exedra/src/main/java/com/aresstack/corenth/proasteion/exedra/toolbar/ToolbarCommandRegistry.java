package com.aresstack.corenth.proasteion.exedra.toolbar;

import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registry of toolbar-visible commands.
 * Backed by the unified {@link CommandRegistry} — a command's
 * {@link ShellCommand#isToolbarVisible()} flag determines default visibility.
 *
 * <p>This class provides toolbar-specific operations while the command
 * identity is shared across menu, toolbar, and shortcut surfaces.
 */
public final class ToolbarCommandRegistry {

    private final CommandRegistry commandRegistry;

    public ToolbarCommandRegistry(CommandRegistry commandRegistry) {
        if (commandRegistry == null) throw new IllegalArgumentException("commandRegistry must not be null");
        this.commandRegistry = commandRegistry;
    }

    /** Find a command by id (delegates to the shared registry). */
    public Optional<ShellCommand> findById(String id) {
        return commandRegistry.findById(id);
    }

    /** All commands that are marked as toolbar-visible by default. */
    public Collection<ShellCommand> getToolbarCommands() {
        List<ShellCommand> result = new ArrayList<>();
        for (ShellCommand cmd : commandRegistry.getAll()) {
            if (cmd.isToolbarVisible()) {
                result.add(cmd);
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    /** All commands (for toolbar configuration dialog). */
    public Collection<ShellCommand> getAll() {
        return commandRegistry.getAll();
    }

    /**
     * Execute a command through the unified {@link CommandRegistry},
     * ensuring execution listeners are notified and events are emitted.
     */
    public void execute(ShellCommand command) {
        commandRegistry.execute(command);
    }
}
