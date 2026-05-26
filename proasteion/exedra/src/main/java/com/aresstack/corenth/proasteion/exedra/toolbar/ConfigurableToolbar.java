package com.aresstack.corenth.proasteion.exedra.toolbar;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;

import javax.swing.JButton;
import javax.swing.JToolBar;
import java.awt.event.ActionEvent;
import java.util.Optional;

/**
 * A configurable toolbar that renders buttons from a {@link ToolbarConfig}
 * backed by a {@link ToolbarCommandRegistry}.
 */
public class ConfigurableToolbar extends JToolBar {

    private final ToolbarCommandRegistry registry;
    private final ToolbarConfigRepository repository;
    private ToolbarConfig config;

    public ConfigurableToolbar(ToolbarCommandRegistry registry, ToolbarConfigRepository repository) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (repository == null) throw new IllegalArgumentException("repository must not be null");
        this.registry = registry;
        this.repository = repository;
        setFloatable(false);
        reload();
    }

    /** Reload configuration from the repository and rebuild buttons. */
    public void reload() {
        config = repository.load(createDefaultConfig());
        rebuildButtons();
    }

    /** Save current config to the repository. */
    public void saveConfig() {
        repository.save(config);
    }

    /** The current config. */
    public ToolbarConfig getConfig() {
        return config;
    }

    /** Replace the config and rebuild. */
    public void setConfig(ToolbarConfig newConfig) {
        this.config = newConfig;
        repository.save(config);
        rebuildButtons();
    }

    private void rebuildButtons() {
        removeAll();
        if (config == null) return;

        for (ToolbarConfig.ToolbarEntry entry : config.getEntries()) {
            if (!entry.isVisible()) continue;
            Optional<ShellCommand> opt = registry.findById(entry.getCommandId());
            if (!opt.isPresent()) continue;
            ShellCommand cmd = opt.get();

            JButton btn = new JButton();
            if (cmd.getIcon() != null) {
                btn.setIcon(cmd.getIcon());
            } else if (cmd.getIconText() != null) {
                btn.setText(cmd.getIconText());
            } else {
                btn.setText(cmd.getLabel());
            }
            btn.setToolTipText(cmd.getLabel());
            btn.setFocusable(false);
            btn.addActionListener((ActionEvent e) -> registry.execute(cmd));
            add(btn);
        }

        revalidate();
        repaint();
    }

    private ToolbarConfig createDefaultConfig() {
        java.util.List<ToolbarConfig.ToolbarEntry> entries = new java.util.ArrayList<>();
        for (ShellCommand cmd : registry.getToolbarCommands()) {
            entries.add(new ToolbarConfig.ToolbarEntry(cmd.getId(), true));
        }
        return new ToolbarConfig(entries);
    }
}
