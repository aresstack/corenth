package com.aresstack.corenth.proasteion.exedra.event.shell;

import com.aresstack.corenth.proasteion.exedra.event.AbstractUiEvent;

/**
 * Published after a shell command has been executed.
 */
public final class CommandExecutedEvent extends AbstractUiEvent<String> {

    /** @param commandId the id of the command that was executed. */
    public CommandExecutedEvent(String commandId) {
        super(commandId);
    }
}
