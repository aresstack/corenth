package com.aresstack.corenth.proasteion.exedra.command;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry of {@link ShellCommand}s.
 * Commands are stored by their dot-separated id and retrieved in insertion order.
 *
 * <p>A single command id is usable for menu, toolbar, and shortcut binding
 * through the unified {@link ShellCommand} interface.
 */
public final class CommandRegistry {

    private final Map<String, ShellCommand> commands = new LinkedHashMap<>();

    /** Register a unified shell command. Replaces any existing command with the same id. */
    public void register(ShellCommand command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        commands.put(command.getId(), command);
    }

    /** Look up a command by its id. */
    public Optional<ShellCommand> findById(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    /** All registered commands in insertion order. */
    public Collection<ShellCommand> getAll() {
        return Collections.unmodifiableCollection(commands.values());
    }

    /** Remove a command by id. */
    public void unregister(String id) {
        commands.remove(id);
    }

    /** Remove all commands. */
    public void clear() {
        commands.clear();
    }
}
