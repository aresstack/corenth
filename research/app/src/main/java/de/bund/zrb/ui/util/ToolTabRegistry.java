package de.bund.zrb.ui.util;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of "tool window" tabs (the four corner tab groups in the left and right
 * drawers) that can be shown or hidden via the application's <em>Ansicht</em> menu.
 *
 * <p>Each registered tab is identified by a stable string key. The registry knows
 * the tab's original home pane (so it can be restored after a hide) but tolerates
 * the user having moved it to a different pane via drag-and-drop —
 * {@link #setVisible(String, boolean)} looks up the current host on every call.
 *
 * <p>Persisted visibility lives in {@code applicationState} under the
 * {@code view.tab.&lt;key&gt;.visible} keys. The Workflow tab is hidden by default
 * (per spec); all others are shown.
 */
public final class ToolTabRegistry {

    /** Set of keys that are hidden by default the very first time the user runs the app. */
    private static final java.util.Set<String> DEFAULT_HIDDEN_KEYS =
            new java.util.HashSet<String>(java.util.Arrays.asList("workflow"));

    /** Snapshot of one tool tab. */
    public static final class Entry {
        public final String key;
        public final String label;          // human-readable label for the menu
        public final JTabbedPane homePane;  // pane the tab originally lived in
        public final int homeIndex;         // intended slot when re-inserting
        public final Component component;
        public final Icon icon;
        public final String title;          // tab title text
        public final String tooltip;
        boolean visible;

        private Entry(String key, String label, JTabbedPane homePane, int homeIndex,
                      Component component, Icon icon, String title, String tooltip,
                      boolean visible) {
            this.key = key;
            this.label = label;
            this.homePane = homePane;
            this.homeIndex = homeIndex;
            this.component = component;
            this.icon = icon;
            this.title = title;
            this.tooltip = tooltip;
            this.visible = visible;
        }

        public boolean isVisible() { return visible; }
    }

    private static final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private static final Map<String, JTabbedPane> panes = new LinkedHashMap<String, JTabbedPane>();
    private static final List<Runnable> changeListeners = new ArrayList<Runnable>();

    /** Suppresses persistence while we are programmatically rearranging tabs. */
    private static boolean restoring = false;

    /** Client property key used to tag a {@link JTabbedPane} with its registry id. */
    private static final String PANE_ID_KEY = "toolTabs.paneId";

    private ToolTabRegistry() { /* static utility */ }

    // ─────────────────────────────────────────────────────────────
    //  Pane registration (required for cross-pane move persistence)
    // ─────────────────────────────────────────────────────────────

    /**
     * Register a {@link JTabbedPane} under a stable id so the registry can persist
     * cross-pane tab moves. Call this before {@link #register(String, String,
     * JTabbedPane, String, Icon, Component, String)} for the same pane.
     */
    public static void registerPane(String paneId, JTabbedPane pane) {
        if (paneId == null || pane == null) return;
        panes.put(paneId, pane);
        pane.putClientProperty(PANE_ID_KEY, paneId);
    }

    /** Look up the registry id of a pane previously passed to {@link #registerPane}. */
    public static String paneIdOf(JTabbedPane pane) {
        if (pane == null) return null;
        Object id = pane.getClientProperty(PANE_ID_KEY);
        return id == null ? null : id.toString();
    }

    /**
     * Register a tab that has already been added to {@code homePane} via the usual
     * {@code addTab(...)} call. Returns the entry so callers can read default
     * visibility immediately. If the previously persisted (or default) visibility
     * says the tab should be hidden, it is removed from {@code homePane} right away.
     */
    public static Entry register(String key, String label, JTabbedPane homePane,
                                 String title, Icon icon, Component component,
                                 String tooltip) {
        if (entries.containsKey(key)) return entries.get(key);
        int idx = homePane.indexOfComponent(component);
        boolean wantVisible = readPersistedVisibility(key);
        Entry e = new Entry(key, label, homePane, idx < 0 ? homePane.getTabCount() : idx,
                component, icon, title, tooltip, true);
        entries.put(key, e);
        if (!wantVisible) {
            setVisible(key, false);
        }
        return e;
    }

    /**
     * After all drawers and all tabs have been registered, move every tab to its
     * previously persisted pane+index (if any). No-op for tabs without persisted
     * layout.
     */
    public static void applyPersistedLayout() {
        Map<String, String> state = loadState();
        if (state == null) return;
        restoring = true;
        try {
            // Pass 1 — move across panes.
            for (Entry e : entries.values()) {
                if (!e.visible) continue;
                String persistedPane = state.get("view.tab." + e.key + ".pane");
                if (persistedPane == null) continue;
                JTabbedPane target = panes.get(persistedPane);
                if (target == null) continue;
                JTabbedPane current = findHost(e.component);
                if (current == null || current == target) continue;
                int srcIdx = current.indexOfComponent(e.component);
                if (srcIdx < 0) continue;
                String t = current.getTitleAt(srcIdx);
                Icon ic = current.getIconAt(srcIdx);
                String tt = current.getToolTipTextAt(srcIdx);
                current.removeTabAt(srcIdx);
                target.addTab(t, ic, e.component, tt);
            }
            // Pass 2 — reorder within each pane.
            for (Entry e : entries.values()) {
                if (!e.visible) continue;
                String persistedIdx = state.get("view.tab." + e.key + ".index");
                if (persistedIdx == null) continue;
                int desired;
                try { desired = Integer.parseInt(persistedIdx); }
                catch (NumberFormatException ex) { continue; }
                JTabbedPane host = findHost(e.component);
                if (host == null) continue;
                int cur = host.indexOfComponent(e.component);
                if (cur < 0 || cur == desired) continue;
                if (desired < 0) desired = 0;
                if (desired >= host.getTabCount()) desired = host.getTabCount() - 1;
                String t = host.getTitleAt(cur);
                Icon ic = host.getIconAt(cur);
                String tt = host.getToolTipTextAt(cur);
                Component tabComp = host.getTabComponentAt(cur);
                boolean enabled = host.isEnabledAt(cur);
                host.removeTabAt(cur);
                if (desired > host.getTabCount()) desired = host.getTabCount();
                host.insertTab(t, ic, e.component, tt, desired);
                if (tabComp != null) host.setTabComponentAt(desired, tabComp);
                host.setEnabledAt(desired, enabled);
            }
        } finally {
            restoring = false;
        }
        fireChanged();
    }

    /** Whether a given tab is currently visible. */
    public static boolean isVisible(String key) {
        Entry e = entries.get(key);
        return e == null || e.visible;
    }

    /**
     * Hide or show the tab identified by {@code key}. When hiding, the tab is
     * removed from whichever pane currently hosts it (in case the user moved it
     * via drag-and-drop). When showing, it is re-inserted into the previously
     * persisted pane+index (falling back to its original {@link Entry#homePane}).
     */
    public static void setVisible(String key, boolean visible) {
        Entry e = entries.get(key);
        if (e == null) return;
        if (e.visible == visible) return;
        if (!visible) {
            JTabbedPane host = findHost(e.component);
            if (host != null) {
                int idx = host.indexOfComponent(e.component);
                if (idx >= 0) {
                    persistLocation(e.key, host, idx);
                    host.removeTabAt(idx);
                }
            }
            e.visible = false;
        } else {
            Map<String, String> state = loadState();
            JTabbedPane target = e.homePane;
            int targetIdx = e.homeIndex;
            if (state != null) {
                String persistedPane = state.get("view.tab." + e.key + ".pane");
                if (persistedPane != null && panes.containsKey(persistedPane)) {
                    target = panes.get(persistedPane);
                }
                String persistedIdx = state.get("view.tab." + e.key + ".index");
                if (persistedIdx != null) {
                    try { targetIdx = Integer.parseInt(persistedIdx); }
                    catch (NumberFormatException ignored) { /* keep default */ }
                }
            }
            if (target == null) target = e.homePane;
            if (targetIdx < 0 || targetIdx > target.getTabCount()) {
                targetIdx = target.getTabCount();
            }
            target.insertTab(e.title, e.icon, e.component, e.tooltip, targetIdx);
            e.visible = true;
        }
        persistVisibility(key, e.visible);
        onLayoutChanged();
        fireChanged();
    }

    /**
     * Persist the current pane id + index of every visible registered tab.
     * Called after drag-and-drop drops and after visibility changes.
     */
    public static void onLayoutChanged() {
        if (restoring) return;
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            if (settings.applicationState == null) {
                settings.applicationState = new LinkedHashMap<String, String>();
            }
            for (Entry e : entries.values()) {
                if (!e.visible) continue;
                JTabbedPane host = findHost(e.component);
                if (host == null) continue;
                String pid = paneIdOf(host);
                int idx = host.indexOfComponent(e.component);
                if (pid != null) {
                    settings.applicationState.put("view.tab." + e.key + ".pane", pid);
                }
                if (idx >= 0) {
                    settings.applicationState.put("view.tab." + e.key + ".index",
                            String.valueOf(idx));
                }
            }
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { /* best-effort persistence */ }
    }

    /** Iteration order = registration order. */
    public static java.util.Collection<Entry> getEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public static void addChangeListener(Runnable r) {
        if (r != null) changeListeners.add(r);
    }

    private static void fireChanged() {
        for (Runnable r : changeListeners) {
            try { r.run(); } catch (RuntimeException ignored) { /* keep robust */ }
        }
    }

    /**
     * Scan all known panes (the ones any registered tab is currently or originally
     * hosted in, plus all registered named panes) to find the one that currently
     * contains {@code component}. Tolerates drag-and-drop moves.
     */
    private static JTabbedPane findHost(Component component) {
        java.util.Set<JTabbedPane> candidates =
                Collections.newSetFromMap(new java.util.IdentityHashMap<JTabbedPane, Boolean>());
        for (Entry e : entries.values()) {
            if (e.homePane != null) candidates.add(e.homePane);
        }
        candidates.addAll(panes.values());
        for (JTabbedPane p : candidates) {
            if (p.indexOfComponent(component) >= 0) return p;
        }
        return null;
    }

    private static Map<String, String> loadState() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            return settings.applicationState;
        } catch (Exception ignored) { return null; }
    }

    private static boolean readPersistedVisibility(String key) {
        Map<String, String> state = loadState();
        if (state != null) {
            String v = state.get("view.tab." + key + ".visible");
            if (v != null) return Boolean.parseBoolean(v);
        }
        return !DEFAULT_HIDDEN_KEYS.contains(key);
    }

    private static void persistVisibility(String key, boolean visible) {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            if (settings.applicationState == null) {
                settings.applicationState = new LinkedHashMap<String, String>();
            }
            settings.applicationState.put("view.tab." + key + ".visible", String.valueOf(visible));
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { /* best-effort persistence */ }
    }

    private static void persistLocation(String key, JTabbedPane host, int idx) {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            if (settings.applicationState == null) {
                settings.applicationState = new LinkedHashMap<String, String>();
            }
            String pid = paneIdOf(host);
            if (pid != null) {
                settings.applicationState.put("view.tab." + key + ".pane", pid);
            }
            if (idx >= 0) {
                settings.applicationState.put("view.tab." + key + ".index", String.valueOf(idx));
            }
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { /* best-effort persistence */ }
    }
}

