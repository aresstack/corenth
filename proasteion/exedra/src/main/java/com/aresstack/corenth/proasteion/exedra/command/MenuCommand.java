package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.Icon;
import javax.swing.KeyStroke;

/**
 * Backward-compatible interface for menu-only commands.
 * New code should implement {@link ShellCommand} directly.
 *
 * @deprecated use {@link ShellCommand} instead for unified menu/toolbar/shortcut support
 */
@Deprecated
public interface MenuCommand extends ShellCommand {

    /** {@inheritDoc} */
    @Override
    default KeyStroke getDefaultAccelerator() {
        return getAccelerator();
    }

    /** Optional keyboard accelerator (may return null). */
    default KeyStroke getAccelerator() {
        return null;
    }
}
