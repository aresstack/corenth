package com.aresstack.corenth.proasteion.exedra.command;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link JMenuBar} from the commands in a {@link CommandRegistry}.
 * The dot-separated command id determines menu hierarchy.
 * An optional menu order can be provided; unordered menus are appended at the end.
 */
public final class MenuTreeBuilder {

    private MenuTreeBuilder() { }

    /**
     * Build a menu bar from all commands in the registry.
     *
     * @param registry  the command registry
     * @param menuOrder optional top-level menu key order (may be null)
     * @return a fully populated JMenuBar
     */
    public static JMenuBar buildMenuBar(CommandRegistry registry, List<String> menuOrder) {
        JMenuBar menuBar = new JMenuBar();
        Node root = new Node();

        for (MenuCommand cmd : registry.getAll()) {
            String[] path = cmd.getId().split("\\.");
            insert(root, path, cmd);
        }

        if (menuOrder != null) {
            for (String key : menuOrder) {
                Node child = root.children.get(key);
                if (child != null) {
                    JMenu menu = buildMenu(child, capitalize(key));
                    if (menu != null && menu.getItemCount() > 0) {
                        menuBar.add(menu);
                    }
                }
            }
        }

        // Append any remaining top-level menus not in the explicit order
        for (Map.Entry<String, Node> entry : root.children.entrySet()) {
            if (menuOrder != null && menuOrder.contains(entry.getKey())) continue;
            JMenu menu = buildMenu(entry.getValue(), capitalize(entry.getKey()));
            if (menu != null && menu.getItemCount() > 0) {
                menuBar.add(menu);
            }
        }

        return menuBar;
    }

    /** Build with no explicit ordering. */
    public static JMenuBar buildMenuBar(CommandRegistry registry) {
        return buildMenuBar(registry, null);
    }

    // ---- internal tree ----

    private static void insert(Node parent, String[] path, MenuCommand cmd) {
        Node current = parent;
        for (int i = 0; i < path.length - 1; i++) {
            current = current.children.computeIfAbsent(path[i], k -> new Node());
        }
        current.leaves.put(path[path.length - 1], cmd);
    }

    private static JMenu buildMenu(Node node, String title) {
        JMenu menu = new JMenu(title);

        // First add leaf commands
        for (Map.Entry<String, MenuCommand> leaf : node.leaves.entrySet()) {
            menu.add(createMenuItem(leaf.getValue()));
        }

        // Then add sub-menus
        for (Map.Entry<String, Node> sub : node.children.entrySet()) {
            JMenu subMenu = buildMenu(sub.getValue(), capitalize(sub.getKey()));
            if (subMenu != null && subMenu.getItemCount() > 0) {
                menu.add(subMenu);
            }
        }

        return menu;
    }

    private static JMenuItem createMenuItem(MenuCommand cmd) {
        JMenuItem item = new JMenuItem(cmd.getLabel());
        item.addActionListener((ActionEvent e) -> cmd.perform());
        if (cmd.getIcon() != null) item.setIcon(cmd.getIcon());
        if (cmd.getAccelerator() != null) item.setAccelerator(cmd.getAccelerator());
        item.setEnabled(cmd.isEnabled());
        return item;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final class Node {
        final Map<String, Node> children = new LinkedHashMap<>();
        final Map<String, MenuCommand> leaves = new LinkedHashMap<>();
    }
}
