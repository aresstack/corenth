package com.aresstack.corenth.proasteion.exedra.shell;

import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.MenuTreeBuilder;
import com.aresstack.corenth.proasteion.exedra.event.UiEventBus;
import com.aresstack.corenth.proasteion.exedra.persistence.ShellStatePersistence;
import com.aresstack.corenth.proasteion.exedra.settings.SettingsCategoryRegistry;
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
 */
public class ShellFrame extends JFrame {

    private final CommandRegistry commandRegistry;
    private final ToolbarCommandRegistry toolbarRegistry;
    private final ToolWindowRegistry toolWindowRegistry;
    private final SettingsCategoryRegistry settingsRegistry;
    private final UiEventBus eventBus;
    private final ShellStatePersistence persistence;

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
     * @param menuOrder          top-level menu order (may be null)
     */
    public ShellFrame(String title,
                      CommandRegistry commandRegistry,
                      ToolbarCommandRegistry toolbarRegistry,
                      ToolbarConfigRepository toolbarConfigRepo,
                      SettingsCategoryRegistry settingsRegistry,
                      UiEventBus eventBus,
                      ShellStatePersistence persistence,
                      List<String> menuOrder) {
        super(title);
        this.commandRegistry = commandRegistry;
        this.toolbarRegistry = toolbarRegistry;
        this.settingsRegistry = settingsRegistry;
        this.eventBus = eventBus;
        this.persistence = persistence;

        // Tool window registry
        this.toolWindowRegistry = new ToolWindowRegistry();
        toolWindowRegistry.bindPane(ToolWindowDescriptor.Position.LEFT_TOP, leftTopPane);
        toolWindowRegistry.bindPane(ToolWindowDescriptor.Position.LEFT_BOTTOM, leftBottomPane);
        toolWindowRegistry.bindPane(ToolWindowDescriptor.Position.RIGHT_TOP, rightTopPane);
        toolWindowRegistry.bindPane(ToolWindowDescriptor.Position.RIGHT_BOTTOM, rightBottomPane);

        // Menu bar
        setJMenuBar(MenuTreeBuilder.buildMenuBar(commandRegistry, menuOrder));

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

        // Draggable tabs between all four panes
        DraggableTabbedPaneSupport.install(leftTopPane, leftBottomPane, rightTopPane, rightBottomPane);

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

    /** Rebuild the menu bar from the current command registry state. */
    public void rebuildMenuBar(List<String> menuOrder) {
        setJMenuBar(MenuTreeBuilder.buildMenuBar(commandRegistry, menuOrder));
        revalidate();
    }

    /** Restore divider positions from persistence. */
    public void restoreDividers() {
        if (persistence == null) return;
        leftSplit.setDividerLocation(persistence.getDividerPosition("left", leftSplit.getHeight() / 2));
        rightSplit.setDividerLocation(persistence.getDividerPosition("right", rightSplit.getHeight() / 2));
        mainHSplit.setDividerLocation(persistence.getDividerPosition("mainH", 200));
        outerSplit.setDividerLocation(persistence.getDividerPosition("outer", outerSplit.getWidth() - 200));
    }

    private void persistState() {
        if (persistence == null) return;
        boolean maximized = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
        persistence.saveWindowBounds(getBounds(), maximized);
        persistence.saveDividerPosition("left", leftSplit.getDividerLocation());
        persistence.saveDividerPosition("right", rightSplit.getDividerLocation());
        persistence.saveDividerPosition("mainH", mainHSplit.getDividerLocation());
        persistence.saveDividerPosition("outer", outerSplit.getDividerLocation());

        // Persist tool visibility
        for (java.util.Map.Entry<String, Boolean> entry : toolWindowRegistry.getVisibilityState().entrySet()) {
            persistence.saveToolVisibility(entry.getKey(), entry.getValue());
        }
    }
}
