package com.aresstack.corenth.proasteion.exedra.settings;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.List;

import static org.junit.Assert.*;

public class SettingsCategoryRegistryTest {

    private SettingsCategoryRegistry registry;

    @Before
    public void setUp() {
        registry = new SettingsCategoryRegistry();
    }

    @Test
    public void registerAndRetrieve() {
        SettingsCategory cat = stubCategory("general", "General");
        registry.register(cat);

        SettingsCategory found = registry.findById("general");
        assertNotNull(found);
        assertEquals("General", found.getTitle());
    }

    @Test
    public void getAll_preservesOrder() {
        registry.register(stubCategory("a", "Alpha"));
        registry.register(stubCategory("b", "Beta"));
        registry.register(stubCategory("c", "Gamma"));

        List<SettingsCategory> all = registry.getAll();
        assertEquals(3, all.size());
        assertEquals("Alpha", all.get(0).getTitle());
        assertEquals("Beta", all.get(1).getTitle());
        assertEquals("Gamma", all.get(2).getTitle());
    }

    @Test
    public void unregister_removes() {
        registry.register(stubCategory("x", "X"));
        registry.unregister("x");
        assertNull(registry.findById("x"));
    }

    @Test
    public void clear_removesAll() {
        registry.register(stubCategory("a", "A"));
        registry.clear();
        assertTrue(registry.getAll().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void register_null_throws() {
        registry.register(null);
    }

    private static SettingsCategory stubCategory(String id, String title) {
        return new SettingsCategory() {
            @Override public String getId() { return id; }
            @Override public String getTitle() { return title; }
            @Override public JComponent getComponent() { return new JPanel(); }
        };
    }
}
