package com.aresstack.corenth.proasteion.exedra.toolwindow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of the full tool-window layout:
 * area per tool id, order within pane, selected tab per pane.
 */
public final class ToolWindowLayout {

    private final Map<String, ToolState> toolStates;
    private final Map<ToolWindowDescriptor.Position, Integer> selectedTabs;

    public ToolWindowLayout(Map<String, ToolState> toolStates,
                            Map<ToolWindowDescriptor.Position, Integer> selectedTabs) {
        this.toolStates = toolStates != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(toolStates))
                : Collections.<String, ToolState>emptyMap();
        this.selectedTabs = selectedTabs != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(selectedTabs))
                : Collections.<ToolWindowDescriptor.Position, Integer>emptyMap();
    }

    public Map<String, ToolState> getToolStates() { return toolStates; }
    public Map<ToolWindowDescriptor.Position, Integer> getSelectedTabs() { return selectedTabs; }

    /**
     * State of a single tool window.
     */
    public static final class ToolState {
        private final ToolWindowDescriptor.Position position;
        private final boolean visible;
        private final int tabIndex;

        public ToolState(ToolWindowDescriptor.Position position, boolean visible, int tabIndex) {
            this.position = position;
            this.visible = visible;
            this.tabIndex = tabIndex;
        }

        public ToolWindowDescriptor.Position getPosition() { return position; }
        public boolean isVisible() { return visible; }
        public int getTabIndex() { return tabIndex; }
    }
}
