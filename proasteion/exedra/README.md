# Exedra

Local UI adapter — generic Swing shell framework.

Exedra is the local user-facing adapter. It provides a reusable Swing application shell
with pluggable commands, toolbars, tool windows, settings, and an event bus.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek
name intentionally. The name marks a boundary in the architecture and should not be
replaced by a generic technical term.

## What was migrated

The following generic UI shell infrastructure was extracted from the MainframeMate
research prototype and made framework-agnostic:

| Component | Package | Description |
|-----------|---------|-------------|
| `CommandRegistry` | `command` | Register/lookup commands by dot-separated ID |
| `MenuCommand` | `command` | Interface for menu-capable commands |
| `MenuTreeBuilder` | `command` | Auto-generates `JMenuBar` from registry contents |
| `ToolbarCommand` | `toolbar` | Interface for toolbar-capable commands |
| `ToolbarCommandRegistry` | `toolbar` | Registry of toolbar commands |
| `ToolbarConfig` / `ToolbarConfigRepository` | `toolbar` | Toolbar layout persistence SPI |
| `ConfigurableToolbar` | `toolbar` | Renders configurable toolbar from config |
| `ToolWindowDescriptor` | `toolwindow` | Descriptor for a dockable tool window |
| `ToolWindowRegistry` | `toolwindow` | Four-pane tool-window management with visibility toggling |
| `DraggableTabbedPaneSupport` | `toolwindow` | Drag-and-drop tabs between panes |
| `SettingsCategory` | `settings` | Interface for a settings page |
| `SettingsCategoryRegistry` | `settings` | Ordered registry of settings categories |
| `OutlookStyleSettingsDialog` | `settings` | Generic category-list settings dialog |
| `UiEvent` / `UiEventBus` / `Subscription` | `event` | Typed pub/sub event bus with unsubscribe handle |
| `ShellStatePersistence` / `ShellStateStore` | `persistence` | Save/restore window bounds, dividers, visibility |
| `ShellFrame` | `shell` | Four-pane main frame wiring all of the above together |

## What was intentionally left out

The following concrete implementations remain in the application layer and are **not**
part of this generic framework:

- `FileTabImpl` and concrete tab implementations
- Connector-specific tabs (Mail, JES, Terminal, Browser, SharePoint, Confluence)
- Chat detail panels
- MCP / Tool / Agent UI
- Video recording UI
- PDF / Mermaid / Preview panels
- Concrete settings categories (FTP, NdV, Wiki, etc.)
- Application-specific toolbar commands and menu commands
- Plugin system (`PluginManager`)

## Usage

```java
CommandRegistry commands = new CommandRegistry();
commands.register(myCommand);

ToolbarCommandRegistry toolbar = new ToolbarCommandRegistry();
toolbar.register(myToolbarCmd);

SettingsCategoryRegistry settings = new SettingsCategoryRegistry();
settings.register(myCategory);

UiEventBus eventBus = new UiEventBus();

ShellFrame frame = new ShellFrame(
    "My App",
    commands, toolbar, toolbarConfigRepo,
    settings, eventBus, persistence,
    Arrays.asList("file", "edit", "view", "help")
);

frame.getToolWindowRegistry().register(
    new ToolWindowDescriptor("explorer", "Explorer",
        ToolWindowDescriptor.Position.LEFT_TOP, myPanel)
);

frame.setVisible(true);
```

## Requirements

- Java 8+
- No external dependencies (pure JDK Swing)
