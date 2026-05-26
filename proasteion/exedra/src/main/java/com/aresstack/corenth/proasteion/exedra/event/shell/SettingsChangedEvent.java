package com.aresstack.corenth.proasteion.exedra.event.shell;

import com.aresstack.corenth.proasteion.exedra.event.AbstractUiEvent;

/**
 * Published when settings have been applied or changed.
 */
public final class SettingsChangedEvent extends AbstractUiEvent<String> {

    /** @param categoryId the id of the settings category that changed, or null for all. */
    public SettingsChangedEvent(String categoryId) {
        super(categoryId);
    }
}
