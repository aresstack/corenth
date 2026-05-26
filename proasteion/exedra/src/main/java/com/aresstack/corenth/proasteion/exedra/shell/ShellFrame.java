package com.aresstack.corenth.proasteion.exedra.shell;

import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.MenuConfig;
import com.aresstack.corenth.proasteion.exedra.command.MenuTreeBuilder;
import com.aresstack.corenth.proasteion.exedra.command.ShortcutRegistry;
import com.aresstack.corenth.proasteion.exedra.command.ShortcutRepository;
import com.aresstack.corenth.proasteion.exedra.event.UiEventBus;
import com.aresstack.corenth.proasteion.exedra.persistence.ShellStatePersistence;
import com.aresstack.corenth.proasteion.exedra.settings.SettingsCategoryRegistry;
import com.aresstack.corenth.proasteion.exedra.settings.SettingsContext;
import com.aresstack.corenth.proasteion.exedra.shell.commands.AboutCommand;
import com.aresstack.corenth.proasteion.exedra.shell.commands.OpenSettingsCommand;
import com.aresstack.corenth.proasteion.exedra.shell.commands.ShortcutSettingsCommand;
import com.aresstack.corenth.proasteion.exedra.shell.commands.ToggleSidebarCommand;
import com.aresstack.corenth.proasteion.exedra.shell.commands.ToggleToolWindowCommand;
import com.aresstack.corenth.proasteion.exedra.shell.commands.ToolbarSettingsCommand;
import com.aresstack.corenth.proasteion.exedra.toolbar.ConfigurableToolbar;
import com.aresstack.corenth.proasteion.exedra.toolbar.ToolbarCommandRegistry;
import com.aresstack.corenth.proasteion.exedra.toolbar.ToolbarConfigRepository;
import com.aresstack.corenth.proasteion.exedra.toolwindow.DraggableTabbedPaneSupport;
import com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowDescriptor;
import com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowRegistry;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Generic four-pane Swing shell frame.
 *
 * <p>Layout:
 * <pre>
 *   ┌──────────────────────────────────────────────┐
 *   │ Menu Bar (generated from CommandRegistry)     │
 *   ├──────────────────────────────────────────────┤
 *   │ Toolbar (configurable)                       │
 *   ├──────┬─────────────────────────────┬─────────┤
 *   │ LT   │                             │  RT     │
 *   │      │      Center Content         │         │
 *   ├──────┤                             ├─────────┤
 *   │ LB   │                             │  RB     │
 *   └──────┴─────────────────────────────┴─────────┘
 * </pre>
 *
 * <p>LT=left-top, LB=left-bottom, RT=right-top, RB=right-bottom tool-window areas.
 *
 * <p>Features:
 * <ul>
 *   <li>Unified command model (menu + toolbar + shortcut from one id)</li>
 *   <li>Shortcut registry with root-pane binding</li>
 *   <li>Default shell commands (settings, shortcuts, toolbar, sidebars, about)</li>
 *   <li>Full tool-window layout persistence</li>
 * </ul>
 */
public class ShellFrame extends JFrame {

    private final CommandRegistry commandRegistry;
    private final ToolbarCommandRegistry toolbarRegistry;
    private final ToolWindowRegistry toolWindowRegistry;
    private final SettingsCategoryRegistry settingsRegistry;
    private final UiEventBus eventBus;
    private final ShellStatePersistence persistence;
    private final ShortcutRegistry shortcutRegistry;
    private final SettingsContext settingsContext;

    private final JTabbedPane leftTopPane = new JTabbedPane(JTabbedPane.TOP);
    private final JTabbedPane leftBottomPane = new JTabbedPane(JTabbedPane.TOP);
    private final JTabbedPane rightTopPane = new JTabbedPane(JTabbedPane.TOP);
    private final JTabbedPane rightBottomPane = new JTabbedPane(JTabbedPane.TOP);
    private final JPanel centerPanel = new JPanel(new BorderLayout());

    private ConfigurableToolbar toolbar;
    private JSplitPane leftSplit;
    private JSplitPane rightSplit;
    private JSplitPane mainHSplit;
    private JSplitPane outerSplit;
    private MenuConfig menuConfig;

    /**
     * Create the shell frame.
     *
     * @param title              frame title
     * @param commandRegistry    menu command registry
     * @param toolbarRegistry    toolbar command registry
     * @param toolbarConfigRepo  toolbar config persistence
     * @param settingsRegistry   settings category registry
     * @param eventBus           UI event bus
     * @param persistence        shell state persistence (may be null)
     * @param menuConfig         menu configuration (may be null for defaults)
     * @param shortcutRegistry   shortcut registry (may be null)
     */
    public ShellFrame(String title,
                      CommandRegistry commandRegistry,
                      ToolbarCommandRegistry toolbarRegistry,
                      ToolbarConfigRepository toolbarConfigRepo,
                      SettingsCategoryRegistry settingsRegistry,
                      UiEventBus eventBus,
                      ShellStatePersistence persistence,
                      MenuConfig menuConfig,
                      ShortcutRegistry shortcutRegistry) {
        super(title);
        this.commandRegistry = commandRegistry;
        this.toolbarRegistry = toolbarRegistry;
        this.settingsRegistry = settingsRegistry;
        this.eventBus = eventBus;
        this.persistence = persistence;
        this.menuConfig = menuConfig;
        this.shortcutRegistry = shortcutRegistry != null ? shortcutRegistry : new ShortcutRegistry();

        // Tool window registry
        this.toolWindowRegistry = new ToolWindowRegistry();
        toolWindowRegistry.setEventBus(eventBus);
        toolWindowRegistry.bindPane(ToolWindowDescriptor.Position.LEFT_TOP, leftTopPane);
        toolWindowRegistry.bindPane(ToolWindowDescriptor.Position.LEFT_BOTTOM, leftBottomPane);
        toolWindowRegistry.bindPane(ToolWindowDescriptor.Position.RIGHT_TOP, rightTopPane);
        toolWindowRegistry.bindPane(ToolWindowDescriptor.Position.RIGHT_BOTTOM, rightBottomPane);

        // Settings context
        this.settingsContext = new SettingsContext(
                this, commandRegistry, this.shortcutRegistry,
                toolbarRegistry, toolWindowRegistry, eventBus);

        // Toolbar
        toolbar = new ConfigurableToolbar(toolbarRegistry, toolbarConfigRepo);
        add(toolbar, BorderLayout.NORTH);

        // Layout
        leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, leftTopPane, leftBottomPane);
        leftSplit.setResizeWeight(0.5);

        rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rightTopPane, rightBottomPane);
        rightSplit.setResizeWeight(0.5);

        mainHSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, centerPanel);
        mainHSplit.setResizeWeight(0.0);

        outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainHSplit, rightSplit);
        outerSplit.setResizeWeight(1.0);

        add(outerSplit, BorderLayout.CENTER);

        // Draggable tabs between all four panes — with model callback
        DraggableTabbedPaneSupport.install(
                (component, sourcePane, targetPane) -> {
                    // Find the tool id for the moved component
                    String toolId = toolWindowRegistry.findIdByComponent(component);
                    if (toolId != null) {
                        ToolWindowDescriptor.Position newPos = toolWindowRegistry.getPositionForPane(targetPane);
                        if (newPos != null) {
                            // Update the internal model (position tracking only, tab already moved)
                            toolWindowRegistry.updatePositionAfterDrag(toolId, newPos);
                            // Emit event
                            if (eventBus != null) {
                                eventBus.publish(new com.aresstack.corenth.proasteion.exedra.event.shell.ToolWindowChangedEvent(
                                        toolId, com.aresstack.corenth.proasteion.exedra.event.shell.ToolWindowChangedEvent.ChangeType.MOVED));
                            }
                        }
                    }
                },
                leftTopPane, leftBottomPane, rightTopPane, rightBottomPane);

        // Register execution listener to emit CommandExecutedEvent
        if (eventBus != null) {
            commandRegistry.addExecutionListener(cmd ->
                    eventBus.publish(new com.aresstack.corenth.proasteion.exedra.event.shell.CommandExecutedEvent(cmd.getId())));
        }

        // Register default shell commands
        registerDefaultCommands(title);

        // Build menu bar (after default commands are registered)
        setJMenuBar(MenuTreeBuilder.buildMenuBar(commandRegistry, menuConfig, this.shortcutRegistry));

        // Apply shortcuts to root pane
        this.shortcutRegistry.applyToRootPane(getRootPane(), commandRegistry);

        // Restore or set default bounds
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle defaultBounds = new Rectangle(
                screen.width / 8, screen.height / 8,
                screen.width * 3 / 4, screen.height * 3 / 4);

        if (persistence != null) {
            setBounds(persistence.getWindowBounds(defaultBounds));
            if (persistence.isMaximized()) {
                setExtendedState(MAXIMIZED_BOTH);
            }
        } else {
            setBounds(defaultBounds);
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Persist state on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                persistState();
            }
        });
    }

    /** Backward-compatible constructor with menu order list. */
    public ShellFrame(String title,
                      CommandRegistry commandRegistry,
                      ToolbarCommandRegistry toolbarRegistry,
                      ToolbarConfigRepository toolbarConfigRepo,
                      SettingsCategoryRegistry settingsRegistry,
                      UiEventBus eventBus,
                      ShellStatePersistence persistence,
                      List<String> menuOrder) {
        this(title, commandRegistry, toolbarRegistry, toolbarConfigRepo,
                settingsRegistry, eventBus, persistence,
                menuOrder != null ? MenuConfig.withOrder(menuOrder) : null, null);
    }

    /** Access the center content panel (applications place their main content here). */
    public JPanel getCenterPanel() { return centerPanel; }

    /** Access the tool window registry. */
    public ToolWindowRegistry getToolWindowRegistry() { return toolWindowRegistry; }

    /** Access the command registry. */
    public CommandRegistry getCommandRegistry() { return commandRegistry; }

    /** Access the toolbar. */
    public ConfigurableToolbar getToolbar() { return toolbar; }

    /** Access the settings registry. */
    public SettingsCategoryRegistry getSettingsRegistry() { return settingsRegistry; }

    /** Access the event bus. */
    public UiEventBus getEventBus() { return eventBus; }

    /** Access the shortcut registry. */
    public ShortcutRegistry getShortcutRegistry() { return shortcutRegistry; }

    /** Access the settings context. */
    public SettingsContext getSettingsContext() { return settingsContext; }

    /**
     * Register a tool window descriptor and automatically create/register a matching
     * {@code ToggleToolWindowCommand} with id {@code "view.tool.<toolId>"}.
     * This keeps tool-window registration and View-menu toggle commands in sync.
     *
     * <p>The menu bar is rebuilt and shortcuts are reapplied so the new toggle command
     * appears in the View menu immediately, without requiring a manual {@link #rebuildMenuBar()} call.
     *
     * @param descriptor the tool window descriptor to register
     */
    public void registerToolWindow(ToolWindowDescriptor descriptor) {
        toolWindowRegistry.register(descriptor);
        ToggleToolWindowCommand toggleCmd = new ToggleToolWindowCommand(descriptor, toolWindowRegistry);
        commandRegistry.register(toggleCmd);
        rebuildMenuBar();
    }

    /** Access the outer split pane (for sidebar toggle commands). */
    public JSplitPane getOuterSplit() { return outerSplit; }

    /** Access the main horizontal split pane (for sidebar toggle commands). */
    public JSplitPane getMainHSplit() { return mainHSplit; }

    /** Rebuild the menu bar from the current command registry state. */
    public void rebuildMenuBar() {
        setJMenuBar(MenuTreeBuilder.buildMenuBar(commandRegistry, menuConfig, shortcutRegistry));
        shortcutRegistry.applyToRootPane(getRootPane(), commandRegistry);
        revalidate();
    }

    /** Rebuild the menu bar with a new config. */
    public void rebuildMenuBar(MenuConfig newConfig) {
        this.menuConfig = newConfig;
        rebuildMenuBar();
    }

    /** Rebuild the menu bar (backward compatible). */
    public void rebuildMenuBar(List<String> menuOrder) {
        rebuildMenuBar(menuOrder != null ? MenuConfig.withOrder(menuOrder) : null);
    }

    /** Restore divider positions and tool-window layout from persistence. */
    public void restoreDividers() {
        if (persistence == null) return;
        leftSplit.setDividerLocation(persistence.getDividerPosition("left", leftSplit.getHeight() / 2));
        rightSplit.setDividerLocation(persistence.getDividerPosition("right", rightSplit.getHeight() / 2));
        mainHSplit.setDividerLocation(persistence.getDividerPosition("mainH", 200));
        outerSplit.setDividerLocation(persistence.getDividerPosition("outer", outerSplit.getWidth() - 200));

        // Restore full tool-window layout (area, order, selected tab)
        com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowLayout layout =
                persistence.loadToolWindowLayout();
        if (layout != null) {
            toolWindowRegistry.applyLayout(layout);
        }
    }

    private void registerDefaultCommands(String appTitle) {
        // Settings
        commandRegistry.register(new OpenSettingsCommand(this, settingsRegistry, settingsContext, eventBus));
        // Toolbar settings
        commandRegistry.register(new ToolbarSettingsCommand(toolbar));
        // Shortcut settings
        commandRegistry.register(new ShortcutSettingsCommand(this, shortcutRegistry, commandRegistry));
        // Sidebar toggles
        commandRegistry.register(new ToggleSidebarCommand(
                "view.leftSidebar", "Toggle Left Sidebar", mainHSplit, ToggleSidebarCommand.Side.LEFT));
        commandRegistry.register(new ToggleSidebarCommand(
                "view.rightSidebar", "Toggle Right Sidebar", outerSplit, ToggleSidebarCommand.Side.RIGHT));
        // About
        commandRegistry.register(new AboutCommand(this, appTitle, "1.0"));
    }

    private void persistState() {
        if (persistence == null) return;
        boolean maximized = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
        persistence.saveWindowBounds(getBounds(), maximized);
        persistence.saveDividerPosition("left", leftSplit.getDividerLocation());
        persistence.saveDividerPosition("right", rightSplit.getDividerLocation());
        persistence.saveDividerPosition("mainH", mainHSplit.getDividerLocation());
        persistence.saveDividerPosition("outer", outerSplit.getDividerLocation());

        // Persist full tool-window layout (area, order, selected tab per pane)
        persistence.saveToolWindowLayout(toolWindowRegistry.getLayout());
    }
}
