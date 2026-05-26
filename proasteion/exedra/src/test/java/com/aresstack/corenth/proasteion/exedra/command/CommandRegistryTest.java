package com.aresstack.corenth.proasteion.exedra.command;

import org.junit.Before;
import org.junit.Test;

import javax.swing.Icon;
import javax.swing.KeyStroke;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.*;

public class CommandRegistryTest {

    private CommandRegistry registry;

    @Before
    public void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    public void registerAndFindById() {
        MenuCommand cmd = stubCommand("file.save", "Save");
        registry.register(cmd);

        Optional<MenuCommand> found = registry.findById("file.save");
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

        Collection<MenuCommand> all = registry.getAll();
        assertEquals(3, all.size());
        String[] labels = all.stream().map(MenuCommand::getLabel).toArray(String[]::new);
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

    private static MenuCommand stubCommand(String id, String label) {
        return new MenuCommand() {
            @Override public String getId() { return id; }
            @Override public String getLabel() { return label; }
            @Override public void perform() { }
        };
    }
}
