package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.MainFrame;
import de.zrb.bund.api.ShortcutMenuCommand;

/**
 * Toggle visibility of the right chat/outline drawer.
 * Shown under the "Ansicht" (View) menu.
 */
public class ToggleRightDrawerMenuCommand extends ShortcutMenuCommand {

    private final MainFrame mainFrame;

    public ToggleRightDrawerMenuCommand(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public String getId() {
        return "view.drawer.right";
    }

    @Override
    public String getLabel() {
        return "\u25B6 Rechte Seitenleiste";
    }

    @Override
    public void perform() {
        if (mainFrame == null) return;
        mainFrame.setRightDrawerVisible(!mainFrame.isRightDrawerVisible());
    }
}

