package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.Icon;
import javax.swing.KeyStroke;

/**
 * A command that can appear in menus and toolbars.
 * The dot-separated {@link #getId() id} determines the menu hierarchy
 * (e.g. {@code "file.save"} → File → Save).
 */
public interface MenuCommand {

    /** Dot-separated path that determines menu placement (e.g. "file.open"). */
    String getId();

    /** Human-readable label for menus and tooltips. */
    String getLabel();

    /** Execute the command. */
    void perform();

    /** Optional icon (may return null). */
    default Icon getIcon() {
        return null;
    }

    /** Optional keyboard accelerator (may return null). */
    default KeyStroke getAccelerator() {
        return null;
    }

    /** Whether the command is currently enabled. */
    default boolean isEnabled() {
        return true;
    }
}
