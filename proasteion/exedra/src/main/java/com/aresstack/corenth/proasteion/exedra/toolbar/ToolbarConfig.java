package com.aresstack.corenth.proasteion.exedra.toolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable configuration describing which toolbar commands are shown and in what order.
 */
public final class ToolbarConfig {

    private final List<ToolbarEntry> entries;

    public ToolbarConfig(List<ToolbarEntry> entries) {
        this.entries = entries != null
                ? Collections.unmodifiableList(new ArrayList<ToolbarEntry>(entries))
                : Collections.<ToolbarEntry>emptyList();
    }

    public List<ToolbarEntry> getEntries() {
        return entries;
    }

    /**
     * A single entry in the toolbar configuration.
     */
    public static final class ToolbarEntry {
        private final String commandId;
        private final boolean visible;

        public ToolbarEntry(String commandId, boolean visible) {
            this.commandId = commandId;
            this.visible = visible;
        }

        public String getCommandId() { return commandId; }
        public boolean isVisible() { return visible; }
    }
}
