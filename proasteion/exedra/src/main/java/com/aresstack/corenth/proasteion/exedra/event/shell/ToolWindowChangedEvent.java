package com.aresstack.corenth.proasteion.exedra.event.shell;

import com.aresstack.corenth.proasteion.exedra.event.AbstractUiEvent;

/**
 * Published when a tool window's visibility or position changes.
 */
public final class ToolWindowChangedEvent extends AbstractUiEvent<String> {

    public enum ChangeType { SHOWN, HIDDEN, MOVED }

    private final ChangeType changeType;

    /**
     * @param toolId     the id of the affected tool window
     * @param changeType what changed
     */
    public ToolWindowChangedEvent(String toolId, ChangeType changeType) {
        super(toolId);
        this.changeType = changeType;
    }

    public ChangeType getChangeType() { return changeType; }
}
