package com.aresstack.corenth.proasteion.exedra.toolbar;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;

/**
 * Backward-compatible marker for toolbar commands.
 * New code should implement {@link ShellCommand} with
 * {@link ShellCommand#isToolbarVisible()} returning true.
 *
 * @deprecated use {@link ShellCommand} directly
 */
@Deprecated
public interface ToolbarCommand extends ShellCommand {

    @Override
    default boolean isToolbarVisible() {
        return true;
    }
}
