package com.aresstack.corenth.proasteion.exedra.toolwindow;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import java.util.Map;

import static org.junit.Assert.*;

public class ToolWindowRegistryTest {

    private ToolWindowRegistry registry;
    private JTabbedPane leftTop;
    private JTabbedPane rightTop;

    @Before
    public void setUp() {
        registry = new ToolWindowRegistry();
        leftTop = new JTabbedPane();
        rightTop = new JTabbedPane();
        registry.bindPane(ToolWindowDescriptor.Position.LEFT_TOP, leftTop);
        registry.bindPane(ToolWindowDescriptor.Position.RIGHT_TOP, rightTop);
    }

    @Test
    public void register_addsTabWhenVisible() {
        ToolWindowDescriptor desc = new ToolWindowDescriptor(
                "bookmarks", "Bookmarks", ToolWindowDescriptor.Position.LEFT_TOP,
                new JLabel("content"), null, true);

        registry.register(desc);
        assertEquals(1, leftTop.getTabCount());
        assertEquals("Bookmarks", leftTop.getTitleAt(0));
    }

    @Test
    public void register_hiddenByDefault_doesNotAddTab() {
        ToolWindowDescriptor desc = new ToolWindowDescriptor(
                "hidden", "Hidden Tool", ToolWindowDescriptor.Position.LEFT_TOP,
                new JLabel("x"), null, false);

        registry.register(desc);
        assertEquals(0, leftTop.getTabCount());
    }

    @Test
    public void setVisible_showsHiddenTab() {
        ToolWindowDescriptor desc = new ToolWindowDescriptor(
                "t1", "Tool1", ToolWindowDescriptor.Position.RIGHT_TOP,
                new JLabel("c"), null, false);
        registry.register(desc);

        registry.setVisible("t1", true);
        assertTrue(registry.isVisible("t1"));
        assertEquals(1, rightTop.getTabCount());
    }

    @Test
    public void setVisible_hidesVisibleTab() {
        ToolWindowDescriptor desc = new ToolWindowDescriptor(
                "t2", "Tool2", ToolWindowDescriptor.Position.LEFT_TOP,
                new JLabel("c"), null, true);
        registry.register(desc);

        registry.setVisible("t2", false);
        assertFalse(registry.isVisible("t2"));
        assertEquals(0, leftTop.getTabCount());
    }

    @Test
    public void getVisibilityState_reflectsCurrentState() {
        registry.register(new ToolWindowDescriptor("a", "A",
                ToolWindowDescriptor.Position.LEFT_TOP, new JLabel(), null, true));
        registry.register(new ToolWindowDescriptor("b", "B",
                ToolWindowDescriptor.Position.RIGHT_TOP, new JLabel(), null, false));

        Map<String, Boolean> state = registry.getVisibilityState();
        assertTrue(state.get("a"));
        assertFalse(state.get("b"));
    }
}
