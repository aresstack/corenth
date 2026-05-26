package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry of keyboard shortcuts mapped to command ids.
 * User-configured shortcuts override the default accelerators defined on {@link ShellCommand}.
 *
 * <p>Each command supports exactly one shortcut binding. Idempotent root-pane binding
 * reliably clears stale key bindings when shortcuts are changed or removed.
 *
 * <p>Use a {@link ShortcutRepository} to load/save the shortcut map.
 */
public final class ShortcutRegistry {

    private static final String ACTION_PREFIX = "exedra.shortcut.";

    private final Map<String, KeyStroke> shortcuts = new LinkedHashMap<>();
    /** Tracks all KeyStroke→actionKey bindings currently installed on the root pane. */
    private final Set<KeyStroke> installedKeyStrokes = new LinkedHashSet<>();

    /** Set the shortcut for a command id (overrides default). Pass null to clear. */
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
        KeyStroke user = getShortcut(command.getId());
        return user != null ? user : command.getDefaultAccelerator();
    }

    /**
     * Load shortcuts from a repository.
     */
    public void load(ShortcutRepository repository) {
        if (repository == null) return;
        shortcuts.clear();
        Map<String, KeyStroke> loaded = repository.load();
        if (loaded != null) {
            for (Map.Entry<String, KeyStroke> e : loaded.entrySet()) {
                if (e.getValue() != null) {
                    shortcuts.put(e.getKey(), e.getValue());
                }
            }
        }
    }

    /**
     * Save shortcuts to a repository.
     */
    public void save(ShortcutRepository repository) {
        if (repository == null) return;
        repository.save(getAll());
    }

    /**
     * Apply all shortcuts as input-map bindings on a root pane.
     * This method is idempotent: it clears all previously installed bindings
     * before installing current ones, ensuring stale shortcuts are fully removed.
     */
    public void applyToRootPane(JRootPane rootPane, CommandRegistry commandRegistry) {
        if (rootPane == null || commandRegistry == null) return;

        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        // Remove ALL previously installed bindings (stale and current)
        for (KeyStroke ks : installedKeyStrokes) {
            inputMap.remove(ks);
        }
        for (ShellCommand cmd : commandRegistry.getAll()) {
            String actionKey = ACTION_PREFIX + cmd.getId();
            actionMap.remove(actionKey);
        }
        installedKeyStrokes.clear();

        // Install current bindings
        for (ShellCommand cmd : commandRegistry.getAll()) {
            String actionKey = ACTION_PREFIX + cmd.getId();

            // Resolve the effective keystroke for this command
            KeyStroke userKs = shortcuts.get(cmd.getId());
            KeyStroke effective = userKs != null ? userKs : cmd.getDefaultAccelerator();

            if (effective != null) {
                javax.swing.AbstractAction action = new javax.swing.AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        commandRegistry.execute(cmd);
                    }
                };
                actionMap.put(actionKey, action);
                inputMap.put(effective, actionKey);
                installedKeyStrokes.add(effective);
            }
        }
    }
}
