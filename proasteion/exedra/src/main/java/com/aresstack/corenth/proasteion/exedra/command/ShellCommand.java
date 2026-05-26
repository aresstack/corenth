package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.Icon;
import javax.swing.KeyStroke;

/**
 * Unified command that can be used in menus, toolbars, and shortcut bindings.
 * A single stable command id is the key for all three surfaces.
 *
 * <p>The dot-separated {@link #getId() id} determines menu hierarchy
 * (e.g. {@code "file.save"} → File → Save) and also serves as the
 * toolbar and shortcut lookup key.
 */
public interface ShellCommand {

    /** Dot-separated path used as menu path, toolbar key, and shortcut key. */
    String getId();

    /** Human-readable label for menus, toolbar tooltips, and shortcut editor. */
    String getLabel();

    /** Execute the command. */
    void perform();

    /** Optional icon (may return null). */
    default Icon getIcon() {
        return null;
    }

    /** Optional icon text (emoji or short string) for toolbar buttons without an icon. */
    default String getIconText() {
        return null;
    }

    /**
     * Default keyboard accelerator. This may be overridden by user-configured shortcuts.
     * Returns null if no default shortcut is assigned.
     */
    default KeyStroke getDefaultAccelerator() {
        return null;
    }

    /** Whether the command is currently enabled. */
    default boolean isEnabled() {
        return true;
    }

    /** Whether this command should appear in the toolbar by default. */
    default boolean isToolbarVisible() {
        return false;
    }
}
