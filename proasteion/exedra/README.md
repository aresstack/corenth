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
| `ShellCommand` | `command` | Unified command interface for menu, toolbar, and shortcut binding |
| `CommandRegistry` | `command` | Register/lookup commands by dot-separated ID |
| `MenuConfig` | `command` | Configurable menu structure with separators, labels, and item ordering |
| `MenuTreeBuilder` | `command` | Auto-generates `JMenuBar` from registry with separator and ordering support |
| `ShortcutRegistry` | `command` | Keyboard shortcut registry with load/save/apply and root-pane binding |
| `ShortcutRepository` | `command` | SPI for shortcut persistence |
| `ToolbarCommandRegistry` | `toolbar` | Registry backed by unified `CommandRegistry` |
| `ToolbarConfig` / `ToolbarConfigRepository` | `toolbar` | Toolbar layout persistence SPI |
| `ConfigurableToolbar` | `toolbar` | Renders configurable toolbar from config |
| `ToolWindowDescriptor` | `toolwindow` | Descriptor with lazy `Supplier<JComponent>` support |
| `ToolWindowRegistry` | `toolwindow` | Four-pane management with full layout persistence (area, order, selected tab) |
| `ToolWindowLayout` | `toolwindow` | Immutable layout snapshot for persistence |
| `DraggableTabbedPaneSupport` | `toolwindow` | Drag-and-drop tabs between panes |
| `SettingsCategory` | `settings` | Interface for a settings page |
| `SettingsCategoryRegistry` | `settings` | Ordered registry with provider support |
| `SettingsCategoryProvider` / `SettingsContext` | `settings` | Dynamic category creation with shell context |
| `OutlookStyleSettingsDialog` | `settings` | Generic category-list settings dialog |
| `UiEvent` / `AbstractUiEvent` | `event` | Timestamped event base with typed payload |
| `UiEventBus` / `Subscription` | `event` | Typed pub/sub event bus with logged failures |
| Shell event types | `event.shell` | `StatusEvent`, `SettingsChangedEvent`, `CommandExecutedEvent`, `ToolWindowChangedEvent` |
| `ShellStatePersistence` / `ShellStateStore` | `persistence` | Save/restore window bounds, dividers, visibility |
| `ShellFrame` | `shell` | Four-pane main frame with shortcut binding and default commands |
| Default commands | `shell.commands` | Settings, shortcuts, toolbar, sidebar toggles, tool-window toggles, about |

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
// Unified command model — one id for menu + toolbar + shortcut
CommandRegistry commands = new CommandRegistry();
commands.register(mySaveCommand); // implements ShellCommand

// Toolbar backed by the same registry
ToolbarCommandRegistry toolbar = new ToolbarCommandRegistry(commands);

// Settings with provider support
SettingsCategoryRegistry settings = new SettingsCategoryRegistry();
settings.registerProvider(myProvider); // creates categories lazily with context

// Shortcut registry with persistence
ShortcutRegistry shortcuts = new ShortcutRegistry();
shortcuts.load(myShortcutRepo);

// Menu configuration with separators and ordering
MenuConfig menuConfig = MenuConfig.builder()
    .menuOrder(Arrays.asList("file", "edit", "view", "tools", "help"))
    .itemOrder("file", Arrays.asList("new", "open", "---", "save", "---", "exit"))
    .label("file", "File")
    .build();

UiEventBus eventBus = new UiEventBus();

ShellFrame frame = new ShellFrame(
    "My App",
    commands, toolbar, toolbarConfigRepo,
    settings, eventBus, persistence,
    menuConfig, shortcuts
);

// Lazy tool window (component only created when first shown)
frame.getToolWindowRegistry().register(
    new ToolWindowDescriptor("explorer", "Explorer",
        ToolWindowDescriptor.Position.LEFT_TOP,
        () -> new ExplorerPanel())
);

frame.setVisible(true);
```

## Requirements

- Java 8+
- No external dependencies (pure JDK Swing)
