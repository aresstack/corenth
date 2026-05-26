package com.aresstack.corenth.proasteion.exedra.command;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central registry of {@link ShellCommand}s.
 * Commands are stored by their dot-separated id and retrieved in insertion order.
 *
 * <p>A single command id is usable for menu, toolbar, and shortcut binding
 * through the unified {@link ShellCommand} interface.
 *
 * <p>Supports execution listeners that are notified after every command execution
 * via {@link #execute(ShellCommand)}.
 */
public final class CommandRegistry {

    private final Map<String, ShellCommand> commands = new LinkedHashMap<>();
    private final List<Consumer<ShellCommand>> executionListeners = new CopyOnWriteArrayList<>();

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

    /**
     * Execute a command and notify all execution listeners.
     * This is the preferred way to invoke commands from menus, toolbar buttons,
     * and shortcut bindings, ensuring events are consistently published.
     */
    public void execute(ShellCommand command) {
        if (command == null || !command.isEnabled()) return;
        command.perform();
        for (Consumer<ShellCommand> listener : executionListeners) {
            try {
                listener.accept(command);
            } catch (Throwable t) {
                // Logged in event bus — here we just don't let a listener failure break execution
            }
        }
    }

    /**
     * Execute a command by id and notify listeners.
     */
    public void execute(String commandId) {
        ShellCommand cmd = commands.get(commandId);
        if (cmd != null) execute(cmd);
    }

    /**
     * Add a listener that is called after every command execution.
     */
    public void addExecutionListener(Consumer<ShellCommand> listener) {
        if (listener != null) executionListeners.add(listener);
    }

    /**
     * Remove an execution listener.
     */
    public void removeExecutionListener(Consumer<ShellCommand> listener) {
        executionListeners.remove(listener);
    }
}
