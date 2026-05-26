package com.aresstack.corenth.proasteion.exedra.shell;

import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.ShortcutRegistry;
import com.aresstack.corenth.proasteion.exedra.event.UiEventBus;
import com.aresstack.corenth.proasteion.exedra.settings.SettingsCategoryRegistry;
import com.aresstack.corenth.proasteion.exedra.toolbar.ToolbarCommandRegistry;
import com.aresstack.corenth.proasteion.exedra.toolbar.ToolbarConfig;
import com.aresstack.corenth.proasteion.exedra.toolbar.ToolbarConfigRepository;
import com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowDescriptor;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.*;

/**
 * Verifies that {@link ShellFrame#registerToolWindow} auto-wires the toggle command
 * and rebuilds the menu bar so the command appears without manual caller intervention.
 */
public class ShellFrameRegisterToolWindowTest {

    /** Minimal in-memory ToolbarConfigRepository for testing. */
    private static class InMemoryToolbarConfigRepository implements ToolbarConfigRepository {
        private ToolbarConfig stored;

        @Override
        public ToolbarConfig load(ToolbarConfig defaultConfig) {
            return stored != null ? stored : defaultConfig;
        }

        @Override
        public void save(ToolbarConfig config) {
            this.stored = config;
        }
    }

    @Test
    public void registerToolWindow_addsToggleCommandAndUpdatesMenu() {
        // ShellFrame extends JFrame and requires a display
        Assume.assumeFalse("Requires a display", GraphicsEnvironment.isHeadless());

        CommandRegistry commands = new CommandRegistry();
        ToolbarCommandRegistry toolbar = new ToolbarCommandRegistry(commands);
        SettingsCategoryRegistry settings = new SettingsCategoryRegistry();
        UiEventBus eventBus = new UiEventBus();
        ShortcutRegistry shortcuts = new ShortcutRegistry();

        ShellFrame frame = new ShellFrame("Test", commands, toolbar,
                new InMemoryToolbarConfigRepository(),
                settings, eventBus, null, null, shortcuts);

        // Register a tool window after construction
        ToolWindowDescriptor desc = new ToolWindowDescriptor(
                "explorer", "Explorer", ToolWindowDescriptor.Position.LEFT_TOP,
                new JLabel("content"), null, true);
        frame.registerToolWindow(desc);

        // 1. The view.tool.explorer command must be in the registry
        assertTrue("Toggle command should be registered",
                commands.findById("view.tool.explorer").isPresent());
        assertEquals("Explorer", commands.findById("view.tool.explorer").get().getLabel());

        // 2. The menu bar should have been rebuilt and include the command
        JMenuBar menuBar = frame.getJMenuBar();
        assertNotNull("Menu bar should exist", menuBar);
        boolean foundInMenu = menuContainsCommand(menuBar, "Explorer");
        assertTrue("Menu should contain the new toggle command label", foundInMenu);

        frame.dispose();
    }

    /**
     * Recursively checks if any menu item in the menu bar has the given text.
     */
    private boolean menuContainsCommand(JMenuBar menuBar, String label) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && menuContainsLabel(menu, label)) {
                return true;
            }
        }
        return false;
    }

    private boolean menuContainsLabel(JMenu menu, String label) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            javax.swing.JMenuItem item = menu.getItem(i);
            if (item != null && label.equals(item.getText())) {
                return true;
            }
            if (item instanceof JMenu) {
                if (menuContainsLabel((JMenu) item, label)) return true;
            }
        }
        return false;
    }
}
