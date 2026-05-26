package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.KeyStroke;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of keyboard shortcuts mapped to command ids.
 * User-configured shortcuts override the default accelerators defined on {@link ShellCommand}.
 *
 * <p>Use a {@link ShortcutRepository} to load/save the shortcut map.
 */
public final class ShortcutRegistry {

    private final Map<String, KeyStroke> shortcuts = new LinkedHashMap<>();

    /** Set a shortcut for a command id (overrides default). */
    public void set(String commandId, KeyStroke keyStroke) {
        if (commandId == null) throw new IllegalArgumentException("commandId must not be null");
        if (keyStroke != null) {
            shortcuts.put(commandId, keyStroke);
        } else {
            shortcuts.remove(commandId);
        }
    }

    /** Get the configured shortcut for a command id. Returns null if none configured. */
    public KeyStroke getShortcut(String commandId) {
        return shortcuts.get(commandId);
    }

    /** Remove user-configured shortcut for a command, reverting to default. */
    public void remove(String commandId) {
        shortcuts.remove(commandId);
    }

    /** Get all configured shortcuts (unmodifiable). */
    public Map<String, KeyStroke> getAll() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(shortcuts));
    }

    /** Clear all user-configured shortcuts. */
    public void clear() {
        shortcuts.clear();
    }

    /**
     * Resolve the effective accelerator for a command: user-configured first,
     * then the command's default.
     */
    public KeyStroke resolve(ShellCommand command) {
        KeyStroke user = shortcuts.get(command.getId());
        return user != null ? user : command.getDefaultAccelerator();
    }

    /**
     * Load shortcuts from a repository.
     */
    public void load(ShortcutRepository repository) {
        if (repository == null) return;
        shortcuts.clear();
        shortcuts.putAll(repository.load());
    }

    /**
     * Save shortcuts to a repository.
     */
    public void save(ShortcutRepository repository) {
        if (repository == null) return;
        repository.save(Collections.unmodifiableMap(new LinkedHashMap<>(shortcuts)));
    }

    /**
     * Apply all shortcuts as input-map bindings on a root pane.
     * Clears previous bindings and installs current ones.
     */
    public void applyToRootPane(javax.swing.JRootPane rootPane, CommandRegistry commandRegistry) {
        if (rootPane == null || commandRegistry == null) return;

        javax.swing.InputMap inputMap = rootPane.getInputMap(
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap actionMap = rootPane.getActionMap();

        // Clear previously installed shell shortcuts
        for (String id : shortcuts.keySet()) {
            actionMap.remove("exedra.shortcut." + id);
        }

        // Also bind default accelerators from commands
        for (ShellCommand cmd : commandRegistry.getAll()) {
            KeyStroke ks = resolve(cmd);
            if (ks != null) {
                String actionKey = "exedra.shortcut." + cmd.getId();
                inputMap.put(ks, actionKey);
                actionMap.put(actionKey, new javax.swing.AbstractAction() {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        if (cmd.isEnabled()) cmd.perform();
                    }
                });
            }
        }
    }
}
