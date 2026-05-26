package com.aresstack.corenth.proasteion.exedra.toolbar;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of toolbar commands available for placement on the configurable toolbar.
 */
public final class ToolbarCommandRegistry {

    private final Map<String, ToolbarCommand> commands = new LinkedHashMap<>();

    public void register(ToolbarCommand command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        commands.put(command.getId(), command);
    }

    public Optional<ToolbarCommand> findById(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    public Collection<ToolbarCommand> getAll() {
        return Collections.unmodifiableCollection(commands.values());
    }

    public void clear() {
        commands.clear();
    }
}
