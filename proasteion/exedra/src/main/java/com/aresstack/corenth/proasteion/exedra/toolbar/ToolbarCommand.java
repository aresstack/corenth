package com.aresstack.corenth.proasteion.exedra.toolbar;

import javax.swing.Icon;

/**
 * A command that can be placed on the configurable toolbar.
 */
public interface ToolbarCommand {

    /** Unique stable identifier. */
    String getId();

    /** Display label (used in config dialog and tooltip). */
    String getLabel();

    /** Execute the command. */
    void perform();

    /** Optional icon text (emoji or short text rendered on the button). */
    default String getIconText() {
        return null;
    }

    /** Optional Swing icon. */
    default Icon getIcon() {
        return null;
    }

    /** Whether the command is currently enabled. */
    default boolean isEnabled() {
        return true;
    }
}
