package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of keyboard shortcuts mapped to command ids.
 * User-configured shortcuts override the default accelerators defined on {@link ShellCommand}.
 *
 * <p>Supports multiple shortcuts per command and idempotent root-pane binding
 * that reliably clears stale key bindings.
 *
 * <p>Use a {@link ShortcutRepository} to load/save the shortcut map.
 */
public final class ShortcutRegistry {

    private static final String ACTION_PREFIX = "exedra.shortcut.";

    private final Map<String, List<KeyStroke>> shortcuts = new LinkedHashMap<>();
    /** Tracks all KeyStroke→actionKey bindings currently installed on the root pane. */
    private final Set<KeyStroke> installedKeyStrokes = new LinkedHashSet<>();

    /** Set a single shortcut for a command id (overrides default). Clears any previous multi-shortcuts. */
    public void set(String commandId, KeyStroke keyStroke) {
        if (commandId == null) throw new IllegalArgumentException("commandId must not be null");
        if (keyStroke != null) {
            List<KeyStroke> list = new ArrayList<>();
            list.add(keyStroke);
            shortcuts.put(commandId, list);
        } else {
            shortcuts.remove(commandId);
        }
    }

    /** Set multiple shortcuts for a command id. */
    public void setAll(String commandId, List<KeyStroke> keyStrokes) {
        if (commandId == null) throw new IllegalArgumentException("commandId must not be null");
        if (keyStrokes == null || keyStrokes.isEmpty()) {
            shortcuts.remove(commandId);
        } else {
            shortcuts.put(commandId, new ArrayList<>(keyStrokes));
        }
    }

    /** Get the first configured shortcut for a command id. Returns null if none configured. */
    public KeyStroke getShortcut(String commandId) {
        List<KeyStroke> list = shortcuts.get(commandId);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    /** Get all configured shortcuts for a command id. */
    public List<KeyStroke> getShortcuts(String commandId) {
        List<KeyStroke> list = shortcuts.get(commandId);
        return list != null ? Collections.unmodifiableList(list) : Collections.<KeyStroke>emptyList();
    }

    /** Remove user-configured shortcut for a command, reverting to default. */
    public void remove(String commandId) {
        shortcuts.remove(commandId);
    }

    /** Get all configured shortcuts (unmodifiable, first shortcut per command). */
    public Map<String, KeyStroke> getAll() {
        Map<String, KeyStroke> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<KeyStroke>> e : shortcuts.entrySet()) {
            if (!e.getValue().isEmpty()) {
                result.put(e.getKey(), e.getValue().get(0));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Get all configured shortcuts including multiple per command. */
    public Map<String, List<KeyStroke>> getAllMulti() {
        Map<String, List<KeyStroke>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<KeyStroke>> e : shortcuts.entrySet()) {
            result.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(result);
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
        for (Map.Entry<String, KeyStroke> e : loaded.entrySet()) {
            set(e.getKey(), e.getValue());
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

            // Gather all effective keystrokes for this command
            List<KeyStroke> effective = new ArrayList<>();
            List<KeyStroke> userKs = shortcuts.get(cmd.getId());
            if (userKs != null && !userKs.isEmpty()) {
                effective.addAll(userKs);
            } else if (cmd.getDefaultAccelerator() != null) {
                effective.add(cmd.getDefaultAccelerator());
            }

            if (!effective.isEmpty()) {
                javax.swing.AbstractAction action = new javax.swing.AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        commandRegistry.execute(cmd);
                    }
                };
                actionMap.put(actionKey, action);

                for (KeyStroke ks : effective) {
                    inputMap.put(ks, actionKey);
                    installedKeyStrokes.add(ks);
                }
            }
        }
    }
}
