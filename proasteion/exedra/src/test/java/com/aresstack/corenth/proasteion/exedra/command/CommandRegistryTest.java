package com.aresstack.corenth.proasteion.exedra.command;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class CommandRegistryTest {

    private CommandRegistry registry;

    @Before
    public void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    public void registerAndFindById() {
        ShellCommand cmd = stubCommand("file.save", "Save");
        registry.register(cmd);

        Optional<ShellCommand> found = registry.findById("file.save");
        assertTrue(found.isPresent());
        assertEquals("Save", found.get().getLabel());
    }

    @Test
    public void findById_missing_returnsEmpty() {
        assertFalse(registry.findById("nonexistent").isPresent());
    }

    @Test
    public void getAll_preservesInsertionOrder() {
        registry.register(stubCommand("a.first", "First"));
        registry.register(stubCommand("b.second", "Second"));
        registry.register(stubCommand("c.third", "Third"));

        Collection<ShellCommand> all = registry.getAll();
        assertEquals(3, all.size());
        String[] labels = all.stream().map(ShellCommand::getLabel).toArray(String[]::new);
        assertArrayEquals(new String[]{"First", "Second", "Third"}, labels);
    }

    @Test
    public void unregister_removesCommand() {
        registry.register(stubCommand("x.y", "Test"));
        registry.unregister("x.y");
        assertFalse(registry.findById("x.y").isPresent());
    }

    @Test
    public void clear_removesAll() {
        registry.register(stubCommand("a", "A"));
        registry.register(stubCommand("b", "B"));
        registry.clear();
        assertEquals(0, registry.getAll().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void register_null_throws() {
        registry.register(null);
    }

    @Test
    public void execute_notifiesListeners() {
        AtomicReference<String> executedId = new AtomicReference<>();
        registry.addExecutionListener(cmd -> executedId.set(cmd.getId()));
        ShellCommand cmd = stubCommand("test.cmd", "Test");
        registry.register(cmd);
        registry.execute(cmd);
        assertEquals("test.cmd", executedId.get());
    }

    @Test
    public void execute_skipsDisabled() {
        AtomicReference<String> executedId = new AtomicReference<>();
        registry.addExecutionListener(c -> executedId.set(c.getId()));
        ShellCommand disabled = new ShellCommand() {
            @Override public String getId() { return "dis"; }
            @Override public String getLabel() { return "Dis"; }
            @Override public void perform() { executedId.set("ran"); }
            @Override public boolean isEnabled() { return false; }
        };
        registry.register(disabled);
        registry.execute(disabled);
        assertNull(executedId.get());
    }

    // ---- Shortcut registry tests ----

    @Test
    public void shortcutRegistry_resolveUsesDefault() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        ShellCommand cmd = stubCommand("file.save", "Save");
        assertNull(shortcuts.resolve(cmd));
    }

    @Test
    public void shortcutRegistry_userOverridesDefault() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        KeyStroke ks = KeyStroke.getKeyStroke("ctrl S");
        shortcuts.set("file.save", ks);
        assertEquals(ks, shortcuts.getShortcut("file.save"));
    }

    @Test
    public void shortcutRegistry_clear_removesAll() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        shortcuts.set("a", KeyStroke.getKeyStroke("ctrl A"));
        shortcuts.set("b", KeyStroke.getKeyStroke("ctrl B"));
        shortcuts.clear();
        assertNull(shortcuts.getShortcut("a"));
        assertNull(shortcuts.getShortcut("b"));
        assertTrue(shortcuts.getAll().isEmpty());
    }

    @Test
    public void shortcutRegistry_remove_reverts() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        shortcuts.set("cmd", KeyStroke.getKeyStroke("ctrl X"));
        shortcuts.remove("cmd");
        assertNull(shortcuts.getShortcut("cmd"));
    }

    @Test
    public void shortcutRegistry_singlePerCommand() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        KeyStroke ks1 = KeyStroke.getKeyStroke("ctrl S");
        shortcuts.set("file.save", ks1);
        assertEquals(ks1, shortcuts.getShortcut("file.save"));

        // Override replaces previous
        KeyStroke ks2 = KeyStroke.getKeyStroke("meta S");
        shortcuts.set("file.save", ks2);
        assertEquals(ks2, shortcuts.getShortcut("file.save"));
    }

    @Test
    public void shortcutRegistry_saveLoadStrings() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        KeyStroke ks = KeyStroke.getKeyStroke("ctrl S");
        shortcuts.set("file.save", ks);

        // Save to a string-based repo
        Map<String, String> saved = new LinkedHashMap<>();
        ShortcutRepository repo = new ShortcutRepository() {
            @Override public Map<String, String> loadStrings() { return saved; }
            @Override public void saveStrings(Map<String, String> s) { saved.putAll(s); }
        };
        shortcuts.save(repo);
        assertFalse(saved.isEmpty());

        // Load into a fresh registry
        ShortcutRegistry loaded = new ShortcutRegistry();
        loaded.load(repo);
        assertNotNull(loaded.getShortcut("file.save"));
    }

    @Test
    public void shortcutRegistry_applyToRootPane_idempotent() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        ShellCommand cmd = stubCommandWithAccelerator("test.cmd", "Test", KeyStroke.getKeyStroke("ctrl T"));
        registry.register(cmd);

        JRootPane rootPane = new JRootPane();
        shortcuts.applyToRootPane(rootPane, registry);

        // Change the shortcut
        shortcuts.set("test.cmd", KeyStroke.getKeyStroke("ctrl N"));
        shortcuts.applyToRootPane(rootPane, registry);

        // Old binding must be gone
        Object oldAction = rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke("ctrl T"));
        assertNull("Stale binding should be removed", oldAction);

        // New binding must be present
        Object newAction = rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke("ctrl N"));
        assertNotNull("New binding should be present", newAction);
    }

    @Test
    public void shortcutRegistry_clearAndReapply_removesAllBindings() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        ShellCommand cmd = stubCommandWithAccelerator("a.b", "AB", KeyStroke.getKeyStroke("ctrl A"));
        registry.register(cmd);
        shortcuts.set("a.b", KeyStroke.getKeyStroke("ctrl B"));

        JRootPane rootPane = new JRootPane();
        shortcuts.applyToRootPane(rootPane, registry);

        // Clear user shortcuts, reapply — should fall back to default accelerator
        shortcuts.clear();
        shortcuts.applyToRootPane(rootPane, registry);

        // ctrl B should be gone (was user override)
        assertNull(rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke("ctrl B")));
        // ctrl A should be present (default accelerator)
        assertNotNull(rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke("ctrl A")));
    }

    @Test
    public void shortcutRegistry_unregisteredCommand_reapplyClearsStaleActionKey() {
        ShortcutRegistry shortcuts = new ShortcutRegistry();
        ShellCommand cmd = stubCommandWithAccelerator("gone.cmd", "Gone", KeyStroke.getKeyStroke("ctrl G"));
        registry.register(cmd);

        JRootPane rootPane = new JRootPane();
        shortcuts.applyToRootPane(rootPane, registry);
        assertNotNull(rootPane.getActionMap().get("exedra.shortcut.gone.cmd"));

        registry.unregister("gone.cmd");
        shortcuts.applyToRootPane(rootPane, registry);

        assertNull(rootPane.getActionMap().get("exedra.shortcut.gone.cmd"));
        assertNull(rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke("ctrl G")));
    }

    private static ShellCommand stubCommand(String id, String label) {
        return new ShellCommand() {
            @Override public String getId() { return id; }
            @Override public String getLabel() { return label; }
            @Override public void perform() { }
        };
    }

    private static ShellCommand stubCommandWithAccelerator(String id, String label, KeyStroke accel) {
        return new ShellCommand() {
            @Override public String getId() { return id; }
            @Override public String getLabel() { return label; }
            @Override public void perform() { }
            @Override public KeyStroke getDefaultAccelerator() { return accel; }
        };
    }
}
