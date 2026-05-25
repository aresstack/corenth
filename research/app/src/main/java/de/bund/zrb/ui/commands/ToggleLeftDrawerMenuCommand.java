package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.MainFrame;
import de.zrb.bund.api.ShortcutMenuCommand;

/**
 * Toggle visibility of the left bookmark/relations drawer.
 * Shown under the "Ansicht" (View) menu.
 */
public class ToggleLeftDrawerMenuCommand extends ShortcutMenuCommand {

    private final MainFrame mainFrame;

    public ToggleLeftDrawerMenuCommand(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public String getId() {
        return "view.drawer.left";
    }

    @Override
    public String getLabel() {
        return "\u25C0 Linke Seitenleiste";
    }

    @Override
    public void perform() {
        if (mainFrame == null) return;
        mainFrame.setLeftDrawerVisible(!mainFrame.isLeftDrawerVisible());
    }
}

