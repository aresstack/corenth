package com.aresstack.corenth.proasteion.exedra.settings;

import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.ShortcutRegistry;
import com.aresstack.corenth.proasteion.exedra.event.UiEventBus;
import com.aresstack.corenth.proasteion.exedra.toolbar.ToolbarCommandRegistry;
import com.aresstack.corenth.proasteion.exedra.toolwindow.ToolWindowRegistry;

import javax.swing.JFrame;

/**
 * Context passed to {@link SettingsCategoryProvider} implementations so they
 * can access shell services without direct coupling to {@code ShellFrame}.
 */
public final class SettingsContext {

    private final JFrame frame;
    private final CommandRegistry commandRegistry;
    private final ShortcutRegistry shortcutRegistry;
    private final ToolbarCommandRegistry toolbarRegistry;
    private final ToolWindowRegistry toolWindowRegistry;
    private final UiEventBus eventBus;

    public SettingsContext(JFrame frame,
                           CommandRegistry commandRegistry,
                           ShortcutRegistry shortcutRegistry,
                           ToolbarCommandRegistry toolbarRegistry,
                           ToolWindowRegistry toolWindowRegistry,
                           UiEventBus eventBus) {
        this.frame = frame;
        this.commandRegistry = commandRegistry;
        this.shortcutRegistry = shortcutRegistry;
        this.toolbarRegistry = toolbarRegistry;
        this.toolWindowRegistry = toolWindowRegistry;
        this.eventBus = eventBus;
    }

    public JFrame getFrame() { return frame; }
    public CommandRegistry getCommandRegistry() { return commandRegistry; }
    public ShortcutRegistry getShortcutRegistry() { return shortcutRegistry; }
    public ToolbarCommandRegistry getToolbarRegistry() { return toolbarRegistry; }
    public ToolWindowRegistry getToolWindowRegistry() { return toolWindowRegistry; }
    public UiEventBus getEventBus() { return eventBus; }
}
