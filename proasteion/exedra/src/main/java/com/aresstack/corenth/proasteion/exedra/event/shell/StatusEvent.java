package com.aresstack.corenth.proasteion.exedra.event.shell;

import com.aresstack.corenth.proasteion.exedra.event.AbstractUiEvent;

/**
 * Published when the shell status message changes (e.g. status bar text).
 */
public final class StatusEvent extends AbstractUiEvent<String> {

    public StatusEvent(String message) {
        super(message);
    }
}
