package com.aresstack.corenth.proasteion.exedra.toolwindow;

import com.aresstack.corenth.proasteion.exedra.event.UiEventBus;
import com.aresstack.corenth.proasteion.exedra.event.shell.ToolWindowChangedEvent;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test(expected = IllegalArgumentException.class)
    public void register_duplicateId_throws() {
        registry.register(new ToolWindowDescriptor(
                "dup", "First", ToolWindowDescriptor.Position.LEFT_TOP,
                new JLabel("a"), null, false));
        registry.register(new ToolWindowDescriptor(
                "dup", "Second", ToolWindowDescriptor.Position.LEFT_TOP,
                new JLabel("b"), null, false));
    }

    @Test(expected = IllegalStateException.class)
    public void register_unboundPane_throws() {
        registry.register(new ToolWindowDescriptor(
                "lb", "Left Bottom", ToolWindowDescriptor.Position.LEFT_BOTTOM,
                new JLabel("x"), null, false));
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

    @Test
    public void lazyComponent_notCreatedUntilShown() {
        AtomicInteger createCount = new AtomicInteger(0);
        ToolWindowDescriptor desc = new ToolWindowDescriptor(
                "lazy", "Lazy Tool", ToolWindowDescriptor.Position.LEFT_TOP,
                () -> { createCount.incrementAndGet(); return new JLabel("lazy"); },
                null, false);

        registry.register(desc);
        assertEquals(0, createCount.get());

        registry.setVisible("lazy", true);
        assertEquals(1, createCount.get());
    }

    @Test
    public void moveTo_changesPosition() {
        ToolWindowDescriptor desc = new ToolWindowDescriptor(
                "mover", "Mover", ToolWindowDescriptor.Position.LEFT_TOP,
                new JLabel("m"), null, true);
        registry.register(desc);
        assertEquals(1, leftTop.getTabCount());

        registry.moveTo("mover", ToolWindowDescriptor.Position.RIGHT_TOP);
        assertEquals(0, leftTop.getTabCount());
        assertEquals(1, rightTop.getTabCount());
    }

    @Test
    public void getLayout_capturesFullState() {
        registry.register(new ToolWindowDescriptor("x", "X",
                ToolWindowDescriptor.Position.LEFT_TOP, new JLabel(), null, true));
        ToolWindowLayout layout = registry.getLayout();
        assertNotNull(layout.getToolStates().get("x"));
        assertEquals(ToolWindowDescriptor.Position.LEFT_TOP, layout.getToolStates().get("x").getPosition());
        assertTrue(layout.getToolStates().get("x").isVisible());
    }

    @Test
    public void applyLayout_restoresState() {
        JLabel comp = new JLabel("x");
        registry.register(new ToolWindowDescriptor("x", "X",
                ToolWindowDescriptor.Position.LEFT_TOP, comp, null, true));

        // Move to right
        registry.moveTo("x", ToolWindowDescriptor.Position.RIGHT_TOP);
        ToolWindowLayout layout = registry.getLayout();

        // Fresh registry
        ToolWindowRegistry fresh = new ToolWindowRegistry();
        JTabbedPane lt2 = new JTabbedPane();
        JTabbedPane rt2 = new JTabbedPane();
        fresh.bindPane(ToolWindowDescriptor.Position.LEFT_TOP, lt2);
        fresh.bindPane(ToolWindowDescriptor.Position.RIGHT_TOP, rt2);
        fresh.register(new ToolWindowDescriptor("x", "X",
                ToolWindowDescriptor.Position.LEFT_TOP, new JLabel(), null, true));
        fresh.applyLayout(layout);

        assertEquals(ToolWindowDescriptor.Position.RIGHT_TOP,
                fresh.getLayout().getToolStates().get("x").getPosition());
    }

    @Test
    public void updatePositionAfterDrag_updatesModelOnly() {
        JLabel comp = new JLabel("m");
        registry.register(new ToolWindowDescriptor("d", "D",
                ToolWindowDescriptor.Position.LEFT_TOP, comp, null, true));

        // Simulate tab already moved by DnD
        leftTop.removeTabAt(0);
        rightTop.addTab("D", comp);

        registry.updatePositionAfterDrag("d", ToolWindowDescriptor.Position.RIGHT_TOP);
        assertEquals(ToolWindowDescriptor.Position.RIGHT_TOP,
                registry.getLayout().getToolStates().get("d").getPosition());
    }

    @Test
    public void setVisible_emitsEvent() {
        UiEventBus bus = new UiEventBus();
        registry.setEventBus(bus);
        List<ToolWindowChangedEvent> events = new ArrayList<>();
        bus.subscribe(ToolWindowChangedEvent.class, events::add);

        registry.register(new ToolWindowDescriptor("ev", "Ev",
                ToolWindowDescriptor.Position.LEFT_TOP, new JLabel(), null, false));
        registry.setVisible("ev", true);
        registry.setVisible("ev", false);

        assertEquals(2, events.size());
        assertEquals(ToolWindowChangedEvent.ChangeType.SHOWN, events.get(0).getChangeType());
        assertEquals(ToolWindowChangedEvent.ChangeType.HIDDEN, events.get(1).getChangeType());
    }

    @Test
    public void moveTo_emitsEvent() {
        UiEventBus bus = new UiEventBus();
        registry.setEventBus(bus);
        List<ToolWindowChangedEvent> events = new ArrayList<>();
        bus.subscribe(ToolWindowChangedEvent.class, events::add);

        registry.register(new ToolWindowDescriptor("mv", "Mv",
                ToolWindowDescriptor.Position.LEFT_TOP, new JLabel(), null, true));
        registry.moveTo("mv", ToolWindowDescriptor.Position.RIGHT_TOP);

        assertEquals(1, events.size());
        assertEquals(ToolWindowChangedEvent.ChangeType.MOVED, events.get(0).getChangeType());
    }

    @Test
    public void findIdByComponent_findsRegistered() {
        JLabel comp = new JLabel("find");
        registry.register(new ToolWindowDescriptor("finder", "Finder",
                ToolWindowDescriptor.Position.LEFT_TOP, comp, null, true));
        assertEquals("finder", registry.findIdByComponent(comp));
    }

    @Test
    public void getPositionForPane_returnsCorrectPosition() {
        assertEquals(ToolWindowDescriptor.Position.LEFT_TOP, registry.getPositionForPane(leftTop));
        assertEquals(ToolWindowDescriptor.Position.RIGHT_TOP, registry.getPositionForPane(rightTop));
        assertNull(registry.getPositionForPane(new JTabbedPane()));
    }

    @Test
    public void applyLayout_restoresTabOrder() {
        // Register three tools at LEFT_TOP
        JLabel compA = new JLabel("A");
        JLabel compB = new JLabel("B");
        JLabel compC = new JLabel("C");
        registry.register(new ToolWindowDescriptor("a", "A",
                ToolWindowDescriptor.Position.LEFT_TOP, compA, null, true));
        registry.register(new ToolWindowDescriptor("b", "B",
                ToolWindowDescriptor.Position.LEFT_TOP, compB, null, true));
        registry.register(new ToolWindowDescriptor("c", "C",
                ToolWindowDescriptor.Position.LEFT_TOP, compC, null, true));

        // Current order: A=0, B=1, C=2
        assertEquals(3, leftTop.getTabCount());
        assertEquals("A", leftTop.getTitleAt(0));

        // Build a layout that reverses the order: C=0, B=1, A=2
        Map<String, ToolWindowLayout.ToolState> toolStates = new java.util.LinkedHashMap<>();
        toolStates.put("c", new ToolWindowLayout.ToolState(
                ToolWindowDescriptor.Position.LEFT_TOP, true, 0));
        toolStates.put("b", new ToolWindowLayout.ToolState(
                ToolWindowDescriptor.Position.LEFT_TOP, true, 1));
        toolStates.put("a", new ToolWindowLayout.ToolState(
                ToolWindowDescriptor.Position.LEFT_TOP, true, 2));
        Map<ToolWindowDescriptor.Position, Integer> selectedTabs = new java.util.LinkedHashMap<>();
        ToolWindowLayout layout = new ToolWindowLayout(toolStates, selectedTabs);

        registry.applyLayout(layout);

        assertEquals("C", leftTop.getTitleAt(0));
        assertEquals("B", leftTop.getTitleAt(1));
        assertEquals("A", leftTop.getTitleAt(2));
    }
}
