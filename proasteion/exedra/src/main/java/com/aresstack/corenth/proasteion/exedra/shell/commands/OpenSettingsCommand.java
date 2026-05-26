package com.aresstack.corenth.proasteion.exedra.shell.commands;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;
import com.aresstack.corenth.proasteion.exedra.event.UiEventBus;
import com.aresstack.corenth.proasteion.exedra.event.shell.SettingsChangedEvent;
import com.aresstack.corenth.proasteion.exedra.settings.OutlookStyleSettingsDialog;
import com.aresstack.corenth.proasteion.exedra.settings.SettingsCategory;
import com.aresstack.corenth.proasteion.exedra.settings.SettingsCategoryRegistry;
import com.aresstack.corenth.proasteion.exedra.settings.SettingsContext;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.Collections;
import java.util.List;

/**
 * Opens the Outlook-style settings dialog. Registered as {@code "tools.settings"}.
 * Publishes {@link SettingsChangedEvent} when settings are applied.
 */
public final class OpenSettingsCommand implements ShellCommand {

    private final JFrame frame;
    private final SettingsCategoryRegistry registry;
    private final SettingsContext context;
    private final UiEventBus eventBus;

    public OpenSettingsCommand(JFrame frame, SettingsCategoryRegistry registry,
                               SettingsContext context, UiEventBus eventBus) {
        this.frame = frame;
        this.registry = registry;
        this.context = context;
        this.eventBus = eventBus;
    }

    /** Backward-compatible constructor (no event bus). */
    public OpenSettingsCommand(JFrame frame, SettingsCategoryRegistry registry, SettingsContext context) {
        this(frame, registry, context, null);
    }

    @Override public String getId() { return "tools.settings"; }
    @Override public String getLabel() { return "Settings..."; }

    @Override
    public void perform() {
        List<SettingsCategory> categories = registry.resolveAll(context);
        if (categories.isEmpty()) return;
        Window owner = SwingUtilities.getWindowAncestor(frame);
        OutlookStyleSettingsDialog dlg = new OutlookStyleSettingsDialog(
                owner != null ? owner : frame, "Settings", categories, Collections.emptyList());
        dlg.setVisible(true);
        if (dlg.wasApplied() && eventBus != null) {
            eventBus.publish(new SettingsChangedEvent("settings"));
        }
    }
}
