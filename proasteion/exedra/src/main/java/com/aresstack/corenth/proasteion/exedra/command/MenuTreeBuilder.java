package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link JMenuBar} from the commands in a {@link CommandRegistry}.
 * The dot-separated command id determines menu hierarchy.
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable top-level menu order</li>
 *   <li>Generic separators via {@link MenuConfig}</li>
 *   <li>Label resolution via {@link MenuConfig}</li>
 *   <li>Submenu ordering within each menu</li>
 * </ul>
 */
public final class MenuTreeBuilder {

    private MenuTreeBuilder() { }

    /**
     * Build a menu bar from all commands in the registry.
     *
     * @param registry   the command registry
     * @param config     menu configuration (ordering, separators, labels); may be null for defaults
     * @param shortcuts  shortcut registry for resolved accelerators; may be null
     * @return a fully populated JMenuBar
     */
    public static JMenuBar buildMenuBar(CommandRegistry registry, MenuConfig config, ShortcutRegistry shortcuts) {
        JMenuBar menuBar = new JMenuBar();
        Node root = new Node();

        for (ShellCommand cmd : registry.getAll()) {
            String[] path = cmd.getId().split("\\.");
            insert(root, path, cmd);
        }

        List<String> menuOrder = config != null ? config.getMenuOrder() : null;

        if (menuOrder != null) {
            for (String key : menuOrder) {
                Node child = root.children.get(key);
                if (child != null) {
                    String label = resolveLabel(key, config);
                    JMenu menu = buildMenu(child, label, key, config, shortcuts, registry);
                    if (menu != null && menu.getItemCount() > 0) {
                        menuBar.add(menu);
                    }
                }
            }
        }

        // Append any remaining top-level menus not in the explicit order
        for (Map.Entry<String, Node> entry : root.children.entrySet()) {
            if (menuOrder != null && menuOrder.contains(entry.getKey())) continue;
            String label = resolveLabel(entry.getKey(), config);
            JMenu menu = buildMenu(entry.getValue(), label, entry.getKey(), config, shortcuts, registry);
            if (menu != null && menu.getItemCount() > 0) {
                menuBar.add(menu);
            }
        }

        return menuBar;
    }

    /** Build with menu order list only (backward compatible). */
    public static JMenuBar buildMenuBar(CommandRegistry registry, List<String> menuOrder) {
        MenuConfig config = menuOrder != null ? MenuConfig.withOrder(menuOrder) : null;
        return buildMenuBar(registry, config, null);
    }

    /** Build with no explicit ordering. */
    public static JMenuBar buildMenuBar(CommandRegistry registry) {
        return buildMenuBar(registry, null, null);
    }

    // ---- internal tree ----

    private static void insert(Node parent, String[] path, ShellCommand cmd) {
        Node current = parent;
        for (int i = 0; i < path.length - 1; i++) {
            current = current.children.computeIfAbsent(path[i], k -> new Node());
        }
        current.leaves.put(path[path.length - 1], cmd);
    }

    private static JMenu buildMenu(Node node, String title, String menuPath,
                                    MenuConfig config, ShortcutRegistry shortcuts,
                                    CommandRegistry registry) {
        JMenu menu = new JMenu(title);

        List<String> itemOrder = config != null ? config.getItemOrder(menuPath) : null;
        boolean addedAny = false;

        // If item order specified, follow it
        if (itemOrder != null) {
            for (String key : itemOrder) {
                if (MenuConfig.SEPARATOR.equals(key) || isSeparatorKey(key)) {
                    if (addedAny) menu.addSeparator();
                    continue;
                }
                // Check leaves first
                ShellCommand leafCmd = node.leaves.get(key);
                if (leafCmd != null) {
                    menu.add(createMenuItem(leafCmd, shortcuts, registry));
                    addedAny = true;
                    continue;
                }
                // Check sub-menus
                Node sub = node.children.get(key);
                if (sub != null) {
                    String subLabel = resolveLabel(key, config);
                    String subPath = menuPath + "." + key;
                    JMenu subMenu = buildMenu(sub, subLabel, subPath, config, shortcuts, registry);
                    if (subMenu != null && subMenu.getItemCount() > 0) {
                        menu.add(subMenu);
                        addedAny = true;
                    }
                }
            }
        }

        // Add remaining leaves not in order
        for (Map.Entry<String, ShellCommand> leaf : node.leaves.entrySet()) {
            if (itemOrder != null && itemOrder.contains(leaf.getKey())) continue;
            // Separator-like command ids: leaf keys starting with "---"
            if (isSeparatorKey(leaf.getKey())) {
                if (addedAny) menu.addSeparator();
                continue;
            }
            menu.add(createMenuItem(leaf.getValue(), shortcuts, registry));
            addedAny = true;
        }

        // Add remaining sub-menus not in order
        for (Map.Entry<String, Node> sub : node.children.entrySet()) {
            if (itemOrder != null && itemOrder.contains(sub.getKey())) continue;
            String subLabel = resolveLabel(sub.getKey(), config);
            String subPath = menuPath + "." + sub.getKey();
            JMenu subMenu = buildMenu(sub.getValue(), subLabel, subPath, config, shortcuts, registry);
            if (subMenu != null && subMenu.getItemCount() > 0) {
                menu.add(subMenu);
                addedAny = true;
            }
        }

        return menu;
    }

    private static JMenuItem createMenuItem(ShellCommand cmd, ShortcutRegistry shortcuts,
                                            CommandRegistry registry) {
        JMenuItem item = new JMenuItem(cmd.getLabel());
        item.addActionListener((ActionEvent e) -> registry.execute(cmd));
        if (cmd.getIcon() != null) item.setIcon(cmd.getIcon());

        // Resolve accelerator: shortcut registry overrides default
        KeyStroke accel = null;
        if (shortcuts != null) {
            accel = shortcuts.getShortcut(cmd.getId());
        }
        if (accel == null) {
            accel = cmd.getDefaultAccelerator();
        }
        if (accel != null) item.setAccelerator(accel);
        item.setEnabled(cmd.isEnabled());
        return item;
    }

    private static String resolveLabel(String key, MenuConfig config) {
        if (config != null) {
            String label = config.getLabel(key);
            if (label != null) return label;
        }
        return capitalize(key);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Returns true for separator-like keys: keys starting with "---".
     * This allows contributors to add separator commands with ids like "file.---1"
     * without central menu config changes.
     */
    private static boolean isSeparatorKey(String key) {
        return key != null && key.startsWith("---");
    }

    private static final class Node {
        final Map<String, Node> children = new LinkedHashMap<>();
        final Map<String, ShellCommand> leaves = new LinkedHashMap<>();
    }
}
