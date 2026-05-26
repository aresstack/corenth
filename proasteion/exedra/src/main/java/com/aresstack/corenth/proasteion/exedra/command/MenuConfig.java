package com.aresstack.corenth.proasteion.exedra.command;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for menu generation: top-level order, item order within menus,
 * separator placement, and label overrides.
 *
 * <p>Use the {@link Builder} for convenient construction.
 */
public final class MenuConfig {

    /** Sentinel value used in item-order lists to denote a separator. */
    public static final String SEPARATOR = "---";

    private final List<String> menuOrder;
    private final Map<String, String> labels;
    private final Map<String, List<String>> itemOrders;

    private MenuConfig(List<String> menuOrder, Map<String, String> labels,
                       Map<String, List<String>> itemOrders) {
        this.menuOrder = menuOrder;
        this.labels = labels;
        this.itemOrders = itemOrders;
    }

    /** Top-level menu ordering (may be null). */
    public List<String> getMenuOrder() {
        return menuOrder;
    }

    /** Resolve a label for a menu/submenu key. Returns null if no override. */
    public String getLabel(String key) {
        return labels.get(key);
    }

    /** Get item order for a given menu path (e.g. "file"). Returns null if not configured. */
    public List<String> getItemOrder(String menuPath) {
        return itemOrders.get(menuPath);
    }

    /** Create a minimal config with only menu order. */
    public static MenuConfig withOrder(List<String> menuOrder) {
        return new MenuConfig(menuOrder, Collections.<String, String>emptyMap(),
                Collections.<String, List<String>>emptyMap());
    }

    /** Create a builder for full configuration. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MenuConfig}.
     */
    public static final class Builder {
        private List<String> menuOrder;
        private final Map<String, String> labels = new HashMap<>();
        private final Map<String, List<String>> itemOrders = new HashMap<>();

        /** Set top-level menu ordering. */
        public Builder menuOrder(List<String> order) {
            this.menuOrder = order;
            return this;
        }

        /** Override label for a menu/submenu key. */
        public Builder label(String key, String label) {
            labels.put(key, label);
            return this;
        }

        /**
         * Set item order within a menu. Use {@link MenuConfig#SEPARATOR} for separator lines.
         *
         * @param menuPath dot-separated menu path (e.g. "file")
         * @param order    list of leaf/submenu keys and SEPARATOR markers
         */
        public Builder itemOrder(String menuPath, List<String> order) {
            itemOrders.put(menuPath, order);
            return this;
        }

        public MenuConfig build() {
            return new MenuConfig(menuOrder, labels, itemOrders);
        }
    }
}
