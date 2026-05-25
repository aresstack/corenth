package de.bund.zrb.ui.drawer;

import de.bund.zrb.model.BookmarkEntry;
import de.bund.zrb.helper.BookmarkHelper;
import de.bund.zrb.ui.util.BookmarkTreeTransferHandler;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class LeftDrawer extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final Consumer<BookmarkEntry> onBookmarkOpen;

    private final JTabbedPane tabbedPane;
    private final JPanel bookmarkPanel;

    // ── Relations tab (split: Dependencies top, Hierarchy bottom) ──
    private final JTree relationsTree;
    private final DefaultTreeModel relationsModel;
    private final DefaultMutableTreeNode relationsRoot;
    private final JPanel relationsPanel;
    private final JLabel relationsStatusLabel;

    // ── Hierarchy sub-panel (bottom half of split) ──
    // Split into two independent tabs/trees:
    //   • Callees ("➡ Ruft auf")     — what THIS source calls. Stays pinned to the
    //     originating source while the user navigates by single‑click; only the
    //     "active" highlight moves through the tree.
    //   • Callers ("⬅ Aufgerufen von") — who calls the CURRENTLY active file. Always
    //     rebuilt for the active tab.
    private final JTabbedPane hierarchyTabs;

    private final JTree calleesTree;
    private final DefaultTreeModel calleesModel;
    private final DefaultMutableTreeNode calleesRoot;
    private final JLabel calleesStatusLabel;

    private final JTree callersTree;
    private final DefaultTreeModel callersModel;
    private final DefaultMutableTreeNode callersRoot;
    private final JLabel callersStatusLabel;

    private final JSplitPane relationsSplitPane;

    private static final int TAB_IDX_CALLEES = 0;
    private static final int TAB_IDX_CALLERS = 1;
    private static final String TAB_TITLE_CALLEES = "Callees";
    private static final String TAB_TITLE_CALLERS = "Caller";

    // ── Per‑tab kind filter state ──
    /** Reference kinds (e.g. "CALLNAT", "PERFORM") currently hidden in the callees tree. */
    private final java.util.Set<String> hiddenCalleesKinds = new java.util.HashSet<String>();
    private final java.util.Set<String> hiddenCallersKinds = new java.util.HashSet<String>();
    /** Group top-level entries by reference kind. */
    private boolean groupCalleesByKind = false;
    private boolean groupCallersByKind = false;
    /** Last unfiltered data — re‑applied whenever the filter changes. */
    private CallHierarchyData lastCalleesData;
    private String lastCalleesObjectName;
    private CallHierarchyData lastCallersData;
    private String lastCallersObjectName;

    private JButton calleesFilterButton;
    private JButton callersFilterButton;
    private JToggleButton calleesGroupToggle;
    private JToggleButton callersGroupToggle;

    /**
     * Name-based regex filter for the callees / callers trees — same component as in
     * {@link de.bund.zrb.ui.NdvConnectionTab}. Operates on the source file name
     * stored per line number (i.e. the file in which the call site lives); entries
     * whose file name does not match the regex are dropped from the absolute hits.
     */
    private final de.bund.zrb.ui.util.RegexNameFilter calleesNameFilter = new de.bund.zrb.ui.util.RegexNameFilter();
    private final de.bund.zrb.ui.util.RegexNameFilter callersNameFilter = new de.bund.zrb.ui.util.RegexNameFilter();
    private JButton calleesRegexButton;
    private JButton callersRegexButton;

    /**
     * Scope key for the hierarchy regex filters — typically the tab type
     * ({@code NDV}, {@code CONFLUENCE}, {@code WIKI}, {@code JES}, {@code FTP},
     * {@code BROWSER}, {@code MAIL}, {@code FILE}, …). Each scope keeps its own
     * persisted regex/prefix selection so switching tab types restores the filter
     * that the user configured for that type.
     */
    private String hierarchyScope = "DEFAULT";

    /** Callback for opening a relation target (e.g. wiki link). */
    private Consumer<RelationEntry> onRelationOpen;

    /** Callback for navigating to a source line in the editor (single click). */
    private java.util.function.IntConsumer onLineNavigate;

    /**
     * Callback for navigating to a source line in a specific file (single click).
     * First arg = source file path (may be null → current tab), second arg = 1-based line number.
     * Takes precedence over {@link #onLineNavigate} when set.
     */
    private java.util.function.BiConsumer<String, Integer> onLineNavigateInFile;

    public LeftDrawer(Consumer<BookmarkEntry> onBookmarkOpen) {
        this.onBookmarkOpen = onBookmarkOpen;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));

        // ═══════════════════════════════════════════════
        //  Top tab group: Bookmarks + Dependencies
        //  Bottom tab group: Hierarchy (Callees + Callers)
        //  Mirrors the right drawer (IntelliJ-style two areas).
        // ═══════════════════════════════════════════════
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);

        // ── Tab 1: Bookmarks (default selected) ──
        bookmarkPanel = new JPanel(new BorderLayout());

        BookmarkEntry rootEntry = new BookmarkEntry("Bookmarks", null, true);
        rootNode = new DefaultMutableTreeNode(rootEntry);
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        tree.setCellRenderer(new BookmarkTreeCellRenderer());
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new BookmarkTreeTransferHandler(this));

        bookmarkPanel.add(new JScrollPane(tree), BorderLayout.CENTER);
        installMouseHandler();

        tabbedPane.addTab("📁 Bookmarks", bookmarkPanel);

        // ── Tab 2: Abhängigkeiten (replaces former "Beziehungen" wrapper) ──
        relationsRoot = new DefaultMutableTreeNode("Beziehungen");
        relationsModel = new DefaultTreeModel(relationsRoot);
        relationsTree = new JTree(relationsModel);
        relationsTree.setRootVisible(false);
        relationsTree.setShowsRootHandles(true);
        relationsTree.setCellRenderer(new RelationTreeCellRenderer());

        // Single click → navigate to line in editor; Double click → open target (NDV, etc.)
        relationsTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = relationsTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (!(node.getUserObject() instanceof RelationEntry)) return;
                RelationEntry entry = (RelationEntry) node.getUserObject();

                if (e.getClickCount() == 1 && entry.getLineNumber() > 0) {
                    if (onLineNavigateInFile != null) {
                        onLineNavigateInFile.accept(entry.getSourceFilePath(), entry.getLineNumber());
                    } else if (onLineNavigate != null) {
                        onLineNavigate.accept(entry.getLineNumber());
                    }
                }
                if (e.getClickCount() == 2 && onRelationOpen != null) {
                    onRelationOpen.accept(entry);
                }
            }
        });

        relationsStatusLabel = new JLabel(" ");
        relationsStatusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        relationsStatusLabel.setFont(relationsStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        // Dependencies panel — header label removed since the tab title carries the name.
        relationsPanel = new JPanel(new BorderLayout());
        relationsPanel.add(new JScrollPane(relationsTree), BorderLayout.CENTER);
        relationsPanel.add(relationsStatusLabel, BorderLayout.SOUTH);

        tabbedPane.addTab("📊 Ressourcen", relationsPanel);
        tabbedPane.setSelectedIndex(0); // Bookmarks by default

        // === Bottom: Hierarchy tabs (Callees + Callers as separate trees) ===
        // ── Callees tree ──
        calleesRoot = new DefaultMutableTreeNode("Callees");
        calleesModel = new DefaultTreeModel(calleesRoot);
        calleesTree = new JTree(calleesModel);
        calleesTree.setRootVisible(false);
        calleesTree.setShowsRootHandles(true);
        calleesTree.setCellRenderer(new RelationTreeCellRenderer());
        calleesTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleHierarchyClick(calleesTree, e, true);
            }
        });
        calleesStatusLabel = new JLabel(" ");
        calleesStatusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        calleesStatusLabel.setFont(calleesStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        calleesFilterButton = createKindFilterButton(true);
        calleesGroupToggle = createKindGroupToggle(true);
        calleesRegexButton = createNameRegexButton(true);
        JPanel calleesToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        calleesToolbar.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        calleesToolbar.add(calleesGroupToggle);
        calleesToolbar.add(calleesFilterButton);
        calleesToolbar.add(calleesRegexButton);
        JPanel calleesPanel = new JPanel(new BorderLayout());
        calleesPanel.add(calleesToolbar, BorderLayout.NORTH);
        calleesPanel.add(new JScrollPane(calleesTree), BorderLayout.CENTER);
        calleesPanel.add(calleesStatusLabel, BorderLayout.SOUTH);

        // ── Callers tree ──
        callersRoot = new DefaultMutableTreeNode("Callers");
        callersModel = new DefaultTreeModel(callersRoot);
        callersTree = new JTree(callersModel);
        callersTree.setRootVisible(false);
        callersTree.setShowsRootHandles(true);
        callersTree.setCellRenderer(new RelationTreeCellRenderer());
        callersTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleHierarchyClick(callersTree, e, false);
            }
        });
        callersStatusLabel = new JLabel(" ");
        callersStatusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        callersStatusLabel.setFont(callersStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        callersFilterButton = createKindFilterButton(false);
        callersGroupToggle = createKindGroupToggle(false);
        callersRegexButton = createNameRegexButton(false);
        JPanel callersToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        callersToolbar.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        callersToolbar.add(callersGroupToggle);
        callersToolbar.add(callersFilterButton);
        callersToolbar.add(callersRegexButton);
        JPanel callersPanel = new JPanel(new BorderLayout());
        callersPanel.add(callersToolbar, BorderLayout.NORTH);
        callersPanel.add(new JScrollPane(callersTree), BorderLayout.CENTER);
        callersPanel.add(callersStatusLabel, BorderLayout.SOUTH);

        hierarchyTabs = new JTabbedPane(JTabbedPane.TOP);
        hierarchyTabs.addTab(TAB_TITLE_CALLEES, calleesPanel);
        hierarchyTabs.addTab(TAB_TITLE_CALLERS, callersPanel);

        // === Split pane (movable divider): top = Bookmarks/Abhängigkeiten,
        // bottom = Callees/Callers. Mirrors the right drawer two-area layout. ===
        relationsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, hierarchyTabs);
        relationsSplitPane.setResizeWeight(0.6); // 60% top, 40% bottom default
        relationsSplitPane.setDividerSize(6);
        relationsSplitPane.setContinuousLayout(true);
        relationsSplitPane.setOneTouchExpandable(true);

        add(relationsSplitPane, BorderLayout.CENTER);

        // Enable IntelliJ-style drag-and-drop between the four tool tab groups.
        // Tabs can be reordered within the same pane and moved between the left
        // top/bottom and the right top/bottom — the center editor area is excluded.
        de.bund.zrb.ui.util.DraggableTabbedPaneSupport.install(tabbedPane);
        de.bund.zrb.ui.util.DraggableTabbedPaneSupport.install(hierarchyTabs);

        // Right-click context menus: "Im Browser anzeigen" for entries with URL.
        installBrowserContextMenu(relationsTree);
        installBrowserContextMenu(calleesTree);
        installBrowserContextMenu(callersTree);

        // Tag panes with stable ids so cross-pane tab moves can be persisted.
        de.bund.zrb.ui.util.ToolTabRegistry.registerPane("left.top", tabbedPane);
        de.bund.zrb.ui.util.ToolTabRegistry.registerPane("left.bottom", hierarchyTabs);

        // Register tabs with the view-menu visibility registry.
        de.bund.zrb.ui.util.ToolTabRegistry.register("bookmarks", "📁 Bookmarks",
                tabbedPane, "📁 Bookmarks", null, bookmarkPanel, null);
        de.bund.zrb.ui.util.ToolTabRegistry.register("dependencies", "📊 Ressourcen",
                tabbedPane, "📊 Ressourcen", null, relationsPanel, null);
        de.bund.zrb.ui.util.ToolTabRegistry.register("callees", "➡ Callees",
                hierarchyTabs, TAB_TITLE_CALLEES, null, calleesPanel, null);
        de.bund.zrb.ui.util.ToolTabRegistry.register("callers", "⬅ Caller",
                hierarchyTabs, TAB_TITLE_CALLERS, null, callersPanel, null);

        // Wire regex filter listeners → re‑render trees + persist + restyle button.
        calleesNameFilter.addChangeListener(() -> {
            applyCalleesFilter();
            updateNameRegexButtonStyle(calleesRegexButton, calleesNameFilter);
            persistHierarchyState();
        });
        callersNameFilter.addChangeListener(() -> {
            applyCallersFilter();
            updateNameRegexButtonStyle(callersRegexButton, callersNameFilter);
            persistHierarchyState();
        });

        refreshBookmarks();
    }

    // ═══════════════════════════════════════════════════════════
    //  Application State persistence
    // ═══════════════════════════════════════════════════════════

    /**
     * Persist the currently selected tab index into the application state map.
     */
    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;
        state.put("drawer.left.selectedTab", String.valueOf(tabbedPane.getSelectedIndex()));
        if (relationsSplitPane != null) {
            state.put("drawer.left.relationsSplitDivider", String.valueOf(relationsSplitPane.getDividerLocation()));
        }
        state.put("drawer.left.callees.groupByKind", String.valueOf(groupCalleesByKind));
        state.put("drawer.left.callers.groupByKind", String.valueOf(groupCallersByKind));
        state.put("drawer.left.callees.hiddenKinds", joinCsv(hiddenCalleesKinds));
        state.put("drawer.left.callers.hiddenKinds", joinCsv(hiddenCallersKinds));
        // Name regex filter is scoped per tab type (NDV / CONFLUENCE / JES / …) so each
        // tab type can keep its own remembered filter.
        state.put(scopedNameKey("callees", "nameRegex"), calleesNameFilter.getRegex());
        state.put(scopedNameKey("callers", "nameRegex"), callersNameFilter.getRegex());
        state.put(scopedNameKey("callees", "namePrefixes"),
                de.bund.zrb.ui.util.RegexNameFilter.joinCsv(calleesNameFilter.getSelectedPrefixes()));
        state.put(scopedNameKey("callers", "namePrefixes"),
                de.bund.zrb.ui.util.RegexNameFilter.joinCsv(callersNameFilter.getSelectedPrefixes()));
    }

    /** State key for a name-regex filter slot, scoped by {@link #hierarchyScope}. */
    private String scopedNameKey(String which, String suffix) {
        return "drawer.left." + which + "." + hierarchyScope + "." + suffix;
    }

    /**
     * Restore the previously persisted tab selection.
     */
    public void restoreApplicationState(Map<String, String> state) {
        if (state == null) return;
        String tabIdx = state.get("drawer.left.selectedTab");
        if (tabIdx != null) {
            try {
                int idx = Integer.parseInt(tabIdx);
                if (idx >= 0 && idx < tabbedPane.getTabCount()) {
                    tabbedPane.setSelectedIndex(idx);
                }
            } catch (NumberFormatException ignored) { /* keep default */ }
        }
        String splitDiv = state.get("drawer.left.relationsSplitDivider");
        if (splitDiv != null && relationsSplitPane != null) {
            try {
                int div = Integer.parseInt(splitDiv);
                if (div > 0) {
                    relationsSplitPane.setDividerLocation(div);
                }
            } catch (NumberFormatException ignored) { /* keep default */ }
        }

        // Restore hierarchy filter + grouping state
        String calleesGroup = state.get("drawer.left.callees.groupByKind");
        if (calleesGroup != null) {
            groupCalleesByKind = Boolean.parseBoolean(calleesGroup);
            if (calleesGroupToggle != null) calleesGroupToggle.setSelected(groupCalleesByKind);
        }
        String callersGroup = state.get("drawer.left.callers.groupByKind");
        if (callersGroup != null) {
            groupCallersByKind = Boolean.parseBoolean(callersGroup);
            if (callersGroupToggle != null) callersGroupToggle.setSelected(groupCallersByKind);
        }
        String calleesHidden = state.get("drawer.left.callees.hiddenKinds");
        if (calleesHidden != null) {
            hiddenCalleesKinds.clear();
            hiddenCalleesKinds.addAll(splitCsv(calleesHidden));
            updateFilterButtonStyle(calleesFilterButton, hiddenCalleesKinds);
        }
        String callersHidden = state.get("drawer.left.callers.hiddenKinds");
        if (callersHidden != null) {
            hiddenCallersKinds.clear();
            hiddenCallersKinds.addAll(splitCsv(callersHidden));
            updateFilterButtonStyle(callersFilterButton, hiddenCallersKinds);
        }
        // Restore name regex filter (silent — restoreState does not fire listeners)
        calleesNameFilter.restoreState(state.get(scopedNameKey("callees", "nameRegex")),
                de.bund.zrb.ui.util.RegexNameFilter.splitCsv(
                        state.get(scopedNameKey("callees", "namePrefixes"))));
        callersNameFilter.restoreState(state.get(scopedNameKey("callers", "nameRegex")),
                de.bund.zrb.ui.util.RegexNameFilter.splitCsv(
                        state.get(scopedNameKey("callers", "namePrefixes"))));
        updateNameRegexButtonStyle(calleesRegexButton, calleesNameFilter);
        updateNameRegexButtonStyle(callersRegexButton, callersNameFilter);
    }

    /**
     * Switch the hierarchy filter scope. Each tab type (e.g. {@code NDV},
     * {@code CONFLUENCE}, {@code WIKI}, {@code JES}, {@code FTP}, {@code BROWSER},
     * {@code MAIL}, {@code FILE}) keeps its own regex/prefix selection. Called by
     * {@link de.bund.zrb.ui.TabbedPaneManager} whenever the active tab type changes.
     */
    public void setHierarchyScope(String scope) {
        String s = (scope == null || scope.isEmpty()) ? "DEFAULT" : scope;
        if (s.equals(hierarchyScope)) return;
        hierarchyScope = s;
        // Load the new scope's persisted filter state without firing listeners.
        java.util.Map<String, String> state;
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            state = settings.applicationState != null
                    ? settings.applicationState
                    : java.util.Collections.<String, String>emptyMap();
        } catch (Exception ex) {
            state = java.util.Collections.emptyMap();
        }
        calleesNameFilter.restoreState(state.get(scopedNameKey("callees", "nameRegex")),
                de.bund.zrb.ui.util.RegexNameFilter.splitCsv(
                        state.get(scopedNameKey("callees", "namePrefixes"))));
        callersNameFilter.restoreState(state.get(scopedNameKey("callers", "nameRegex")),
                de.bund.zrb.ui.util.RegexNameFilter.splitCsv(
                        state.get(scopedNameKey("callers", "namePrefixes"))));
        updateNameRegexButtonStyle(calleesRegexButton, calleesNameFilter);
        updateNameRegexButtonStyle(callersRegexButton, callersNameFilter);
        // Re-render the trees so the new filter takes effect on the already-loaded data.
        applyCalleesFilter();
        applyCallersFilter();
    }

    private static String joinCsv(java.util.Set<String> set) {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String s : set) {
            if (!first) sb.append(',');
            sb.append(s);
            first = false;
        }
        return sb.toString();
    }

    private static java.util.List<String> splitCsv(String csv) {
        if (csv == null || csv.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /**
     * Persist hierarchy filter + grouping state to settings.json immediately.
     * Called whenever the user toggles the group button or filter checkboxes so
     * the choice survives application restarts (as requested in the spec).
     */
    private void persistHierarchyState() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            if (settings.applicationState == null) {
                settings.applicationState = new java.util.LinkedHashMap<String, String>();
            }
            addApplicationState(settings.applicationState);
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { /* best-effort persistence */ }
    }

    // ═══════════════════════════════════════════════════════════
    //  Relations API (called by TabbedPaneManager on tab switch)
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the relations tree with the given entries (flat list).
     * @param sectionLabel root label, e.g. "Wiki-Links" or "Dependencies"
     * @param entries      list of relation entries to display
     */
    public void updateRelations(String sectionLabel, List<RelationEntry> entries) {
        relationsRoot.removeAllChildren();

        if (entries == null || entries.isEmpty()) {
            relationsRoot.add(new DefaultMutableTreeNode("(keine)"));
        } else {
            for (RelationEntry entry : entries) {
                relationsRoot.add(new DefaultMutableTreeNode(entry));
            }
        }

        relationsModel.reload();
        relationsStatusLabel.setText(entries != null ? entries.size() + " Beziehungen" : " ");

        // Expand all
        for (int i = 0; i < relationsTree.getRowCount(); i++) {
            relationsTree.expandRow(i);
        }
    }

    /**
     * Update the relations tree with grouped dependency sections.
     * Each section is displayed as a collapsible group node with its entries underneath.
     *
     * @param sectionLabel overall label (for status bar)
     * @param sections     ordered map: group label → list of entries
     * @param totalCount   total number of entries across all groups
     */
    public void updateRelationsGrouped(String sectionLabel,
                                       java.util.Map<String, List<RelationEntry>> sections,
                                       int totalCount) {
        relationsRoot.removeAllChildren();

        if (sections == null || sections.isEmpty()) {
            relationsRoot.add(new DefaultMutableTreeNode("(keine Daten)"));
        } else {
            for (java.util.Map.Entry<String, List<RelationEntry>> section : sections.entrySet()) {
                String groupLabel = section.getKey() + " (" + section.getValue().size() + ")";
                DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupLabel);
                for (RelationEntry entry : section.getValue()) {
                    groupNode.add(new DefaultMutableTreeNode(entry));
                }
                relationsRoot.add(groupNode);
            }
        }

        relationsModel.reload();
        relationsStatusLabel.setText(totalCount + " Einträge" +
                (sections != null ? " in " + sections.size() + " Gruppen" : ""));

        // Expand all
        for (int i = 0; i < relationsTree.getRowCount(); i++) {
            relationsTree.expandRow(i);
        }
    }

    /**
     * Show a placeholder message (e.g. for program tabs where dependencies aren't implemented yet).
     */
    public void showRelationsPlaceholder(String message) {
        relationsRoot.removeAllChildren();
        relationsRoot.add(new DefaultMutableTreeNode(message));
        relationsModel.reload();
        relationsStatusLabel.setText(" ");
    }

    /**
     * Show a loading indicator in the relations tree.
     */
    public void showRelationsLoading() {
        relationsRoot.removeAllChildren();
        relationsRoot.add(new DefaultMutableTreeNode("⏳ Lade Beziehungen…"));
        relationsModel.reload();
        relationsStatusLabel.setText(" ");
    }

    /**
     * Clear relations and call hierarchy (no tab selected).
     */
    public void clearRelations() {
        relationsRoot.removeAllChildren();
        relationsModel.reload();
        relationsStatusLabel.setText(" ");
        clearCallHierarchy();
    }

    public void setOnRelationOpen(Consumer<RelationEntry> callback) {
        this.onRelationOpen = callback;
    }

    public void setOnLineNavigate(java.util.function.IntConsumer callback) {
        this.onLineNavigate = callback;
    }

    /**
     * Set the file-aware navigation callback. When set, this takes precedence over
     * {@link #setOnLineNavigate(java.util.function.IntConsumer)} for single-click navigation
     * in the relations and hierarchy trees. The callback receives the source file path
     * (may be {@code null} → use current tab) and the 1-based line number.
     */
    public void setOnLineNavigateInFile(java.util.function.BiConsumer<String, Integer> callback) {
        this.onLineNavigateInFile = callback;
    }

    // ═══════════════════════════════════════════════════════════
    //  Hierarchy API (bottom half of split — split into Callees + Callers tabs)
    // ═══════════════════════════════════════════════════════════

    /**
     * Update both hierarchy tabs (callees + callers). Convenience wrapper.
     */
    public void updateCallHierarchy(CallHierarchyData calleesRoot,
                                    CallHierarchyData callersRoot,
                                    String objectName) {
        updateCallHierarchy(calleesRoot, callersRoot, objectName, null, null);
    }

    /**
     * Update both hierarchy tabs (callees + callers). The label parameters are
     * accepted for backward compatibility but ignored (tab titles now carry the count).
     */
    public void updateCallHierarchy(CallHierarchyData calleesRoot,
                                    CallHierarchyData callersRoot,
                                    String objectName,
                                    String calleesLabel,
                                    String callersLabel) {
        updateCallees(calleesRoot, calleesLabel, objectName);
        updateCallers(callersRoot, callersLabel, objectName);
    }

    /**
     * Update only the callees ("Callees") tree. Children are added directly to the
     * (invisible) root — no group wrapper node. The count is shown in the tab title.
     *
     * @param calleesRootData hierarchy root — may be {@code null}/empty (renders placeholder)
     * @param customLabel     ignored (legacy parameter); kept for API stability
     * @param objectName      name of the originating object (for the status label)
     */
    public void updateCallees(CallHierarchyData calleesRootData, String customLabel, String objectName) {
        this.lastCalleesData = calleesRootData;
        this.lastCalleesObjectName = objectName;
        applyCalleesFilter();
    }

    /**
     * Update only the callers ("Caller") tree. The callees tree is left untouched.
     */
    public void updateCallers(CallHierarchyData callersRootData, String customLabel, String objectName) {
        this.lastCallersData = callersRootData;
        this.lastCallersObjectName = objectName;
        applyCallersFilter();
    }

    /** Re‑render the callees tree from {@link #lastCalleesData} applying {@link #hiddenCalleesKinds}. */
    private void applyCalleesFilter() {
        this.calleesRoot.removeAllChildren();
        int totalAll = 0;
        int totalVisible = 0;
        boolean hasData = lastCalleesData != null && !lastCalleesData.getChildren().isEmpty();
        if (hasData) {
            int[] counts = groupCalleesByKind
                    ? addGroupedHierarchyChildren(this.calleesRoot, lastCalleesData.getChildren(),
                            hiddenCalleesKinds, calleesNameFilter)
                    : addFilteredHierarchyChildren(this.calleesRoot, lastCalleesData.getChildren(),
                            hiddenCalleesKinds, calleesNameFilter);
            totalAll = counts[0];
            totalVisible = counts[1];
        }
        boolean filterActive = !hiddenCalleesKinds.isEmpty() || calleesNameFilter.isActive();
        if (this.calleesRoot.getChildCount() == 0) {
            String msg = hasData && filterActive
                    ? "(keine Treffer für Filter)"
                    : "(keine ausgehenden Aufrufe)";
            this.calleesRoot.add(new DefaultMutableTreeNode(msg));
        }
        calleesModel.reload();
        setCalleesTabTitle(totalVisible, totalAll, filterActive);
        calleesStatusLabel.setText(formatStatus(lastCalleesObjectName, totalVisible, totalAll,
                filterActive));
        for (int i = 0; i < Math.min(calleesTree.getRowCount(), 20); i++) {
            calleesTree.expandRow(i);
        }
        updateFilterButtonStyle(calleesFilterButton, hiddenCalleesKinds);
    }

    /** Re‑render the callers tree from {@link #lastCallersData} applying {@link #hiddenCallersKinds}. */
    private void applyCallersFilter() {
        this.callersRoot.removeAllChildren();
        int totalAll = 0;
        int totalVisible = 0;
        boolean hasData = lastCallersData != null && !lastCallersData.getChildren().isEmpty();
        if (hasData) {
            int[] counts = groupCallersByKind
                    ? addGroupedHierarchyChildren(this.callersRoot, lastCallersData.getChildren(),
                            hiddenCallersKinds, callersNameFilter)
                    : addFilteredHierarchyChildren(this.callersRoot, lastCallersData.getChildren(),
                            hiddenCallersKinds, callersNameFilter);
            totalAll = counts[0];
            totalVisible = counts[1];
        }
        boolean filterActive = !hiddenCallersKinds.isEmpty() || callersNameFilter.isActive();
        if (this.callersRoot.getChildCount() == 0) {
            String msg = hasData && filterActive
                    ? "(keine Treffer für Filter)"
                    : "(keine eingehenden Aufrufe)";
            this.callersRoot.add(new DefaultMutableTreeNode(msg));
        }
        callersModel.reload();
        setCallersTabTitle(totalVisible, totalAll, filterActive);
        callersStatusLabel.setText(formatStatus(lastCallersObjectName, totalVisible, totalAll,
                filterActive));
        for (int i = 0; i < Math.min(callersTree.getRowCount(), 20); i++) {
            callersTree.expandRow(i);
        }
        updateFilterButtonStyle(callersFilterButton, hiddenCallersKinds);
    }

    /**
     * Group top-level entries by reference kind. Each kind becomes a group node with
     * the matching entries underneath (entries keep their own subtree).
     */
    private int[] addGroupedHierarchyChildren(DefaultMutableTreeNode parent,
                                               List<CallHierarchyData> children,
                                               java.util.Set<String> hiddenKinds,
                                               de.bund.zrb.ui.util.RegexNameFilter nameFilter) {
        // Preserve first-appearance order of kinds
        java.util.LinkedHashMap<String, java.util.List<CallHierarchyData>> byKind =
                new java.util.LinkedHashMap<String, java.util.List<CallHierarchyData>>();
        int total = 0;
        for (CallHierarchyData child : children) {
            int[] sub = countAllNodes(child.getChildren());
            total += 1 + sub[0];
            String kind = child.getReferenceKind() != null ? child.getReferenceKind() : "—";
            java.util.List<CallHierarchyData> list = byKind.get(kind);
            if (list == null) {
                list = new java.util.ArrayList<CallHierarchyData>();
                byKind.put(kind, list);
            }
            list.add(child);
        }

        int visible = 0;
        for (java.util.Map.Entry<String, java.util.List<CallHierarchyData>> e : byKind.entrySet()) {
            String kind = e.getKey();
            if (hiddenKinds.contains(kind)) continue;
            java.util.List<CallHierarchyData> entries = e.getValue();
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(); // label set after counting
            int groupVisible = 0;
            for (CallHierarchyData c : entries) {
                if (!nameMatches(c, nameFilter)) continue;
                RelationEntry entry = new RelationEntry(
                        c.getDisplayText(), c.getTargetPath(),
                        c.isRecursive() ? "CALL_RECURSIVE" : "CALL_HIERARCHY",
                        c.getLineNumber(), c.getSourceFilePath(), c.getReferenceKind());
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
                // Subtree keeps full hierarchy (no further kind grouping); respect both filters.
                int[] subVis = addFilteredHierarchyChildren(node, c.getChildren(), hiddenKinds, nameFilter);
                visible += 1 + subVis[1];
                groupVisible++;
                groupNode.add(node);
            }
            if (groupVisible == 0) continue; // entire group filtered out by name regex
            groupNode.setUserObject(kindIcon(kind) + " " + kind + " (" + groupVisible + ")");
            parent.add(groupNode);
        }
        return new int[]{total, visible};
    }

    private static String formatStatus(String objectName, int visible, int total, boolean filterActive) {
        if (objectName == null) return " ";
        if (filterActive) return objectName + " — " + visible + "/" + total + " Knoten";
        return objectName + " — " + total + " Knoten";
    }

    /**
     * Recursively add children to {@code parent}, skipping nodes whose {@code referenceKind}
     * is in {@code hiddenKinds}. A hidden node also drops its entire subtree.
     *
     * @return {@code int[2]} = {totalNodes (unfiltered), visibleNodes (after filter)}
     */
    private int[] addFilteredHierarchyChildren(DefaultMutableTreeNode parent,
                                                List<CallHierarchyData> children,
                                                java.util.Set<String> hiddenKinds,
                                                de.bund.zrb.ui.util.RegexNameFilter nameFilter) {
        int total = 0;
        int visible = 0;
        for (CallHierarchyData child : children) {
            String kind = child.getReferenceKind();
            boolean isHidden = kind != null && hiddenKinds.contains(kind);
            boolean nameOk = nameMatches(child, nameFilter);
            // Recurse to count all descendants regardless of visibility
            int[] subTotalsAll = countAllNodes(child.getChildren());
            int subTotalAll = subTotalsAll[0];
            total += 1 + subTotalAll;
            if (isHidden || !nameOk) continue;

            RelationEntry entry = new RelationEntry(
                    child.getDisplayText(),
                    child.getTargetPath(),
                    child.isRecursive() ? "CALL_RECURSIVE" : "CALL_HIERARCHY",
                    child.getLineNumber(),
                    child.getSourceFilePath(),
                    child.getReferenceKind()
            );
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
            int[] subVisible = addFilteredHierarchyChildren(node, child.getChildren(), hiddenKinds, nameFilter);
            // subVisible[0] equals subTotalAll (recomputed inside), we use subVisible[1]
            visible += 1 + subVisible[1];
            parent.add(node);
        }
        return new int[]{total, visible};
    }

    /**
     * Test whether a hierarchy node passes the name regex filter. The filter is
     * applied to the per‑line stored {@link CallHierarchyData#getSourceFilePath()},
     * falling back to {@link CallHierarchyData#getTargetPath()} when the source
     * file is unknown. Hits that don't match are removed from the absolute results.
     */
    private static boolean nameMatches(CallHierarchyData node,
                                       de.bund.zrb.ui.util.RegexNameFilter filter) {
        if (filter == null || !filter.isActive()) return true;
        String candidate = extractName(node.getSourceFilePath());
        if (candidate == null) candidate = extractName(node.getTargetPath());
        if (candidate == null) candidate = node.getDisplayText();
        return filter.matches(candidate);
    }

    /** Extract the last path segment (object name) from a URL/URI-like path. */
    private static String extractName(String path) {
        if (path == null || path.isEmpty()) return null;
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String tail = sep >= 0 ? path.substring(sep + 1) : path;
        // Drop trailing query/anchor and file extension to land on the object name
        int q = tail.indexOf('?');
        if (q >= 0) tail = tail.substring(0, q);
        int dot = tail.lastIndexOf('.');
        if (dot > 0) tail = tail.substring(0, dot);
        return tail.isEmpty() ? null : tail;
    }

    private int[] countAllNodes(List<CallHierarchyData> children) {
        int total = 0;
        for (CallHierarchyData child : children) {
            int[] sub = countAllNodes(child.getChildren());
            total += 1 + sub[0];
        }
        return new int[]{total, total};
    }

    /** Collect every distinct {@code referenceKind} found anywhere in the data (in display order). */
    private java.util.List<String> collectKinds(CallHierarchyData root) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<String>();
        if (root != null) collectKindsRecursive(root.getChildren(), set);
        return new java.util.ArrayList<String>(set);
    }

    private void collectKindsRecursive(List<CallHierarchyData> children, java.util.Set<String> out) {
        for (CallHierarchyData c : children) {
            if (c.getReferenceKind() != null && !c.getReferenceKind().isEmpty()) {
                out.add(c.getReferenceKind());
            }
            collectKindsRecursive(c.getChildren(), out);
        }
    }

    /** Create the small "Filter" button for the callees (true) or callers (false) tab. */
    private JButton createKindFilterButton(final boolean isCallees) {
        final JButton btn = new JButton("\uD83D\uDD3D Filter"); // 🔽 Filter
        btn.setToolTipText("Referenz-Typen filtern (CALLNAT, PERFORM, INCLUDE, …)");
        btn.setMargin(new Insets(1, 6, 1, 6));
        btn.setFocusable(false);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.addActionListener(e -> {
            JPopupMenu popup = createKindFilterPopup(isCallees);
            popup.show(btn, 0, btn.getHeight());
        });
        return btn;
    }

    /** Create the toggle button to group entries by reference kind. */
    private JToggleButton createKindGroupToggle(final boolean isCallees) {
        final JToggleButton btn = new JToggleButton("\uD83D\uDCC1"); // 📁
        btn.setToolTipText("Nach Referenz-Typ gruppieren (CALLNAT, PERFORM, …)");
        btn.setMargin(new Insets(1, 6, 1, 6));
        btn.setFocusable(false);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setSelected(isCallees ? groupCalleesByKind : groupCallersByKind);
        btn.addActionListener(e -> {
            if (isCallees) {
                groupCalleesByKind = btn.isSelected();
                applyCalleesFilter();
            } else {
                groupCallersByKind = btn.isSelected();
                applyCallersFilter();
            }
            persistHierarchyState();
        });
        return btn;
    }

    private JPopupMenu createKindFilterPopup(final boolean isCallees) {
        final java.util.Set<String> hidden = isCallees ? hiddenCalleesKinds : hiddenCallersKinds;
        CallHierarchyData data = isCallees ? lastCalleesData : lastCallersData;
        java.util.List<String> kinds = collectKinds(data);

        JPopupMenu popup = new JPopupMenu();

        JMenuItem all = new JMenuItem("Alle anzeigen");
        all.addActionListener(e -> {
            hidden.clear();
            if (isCallees) applyCalleesFilter(); else applyCallersFilter();
            persistHierarchyState();
        });
        popup.add(all);

        JMenuItem invert = new JMenuItem("Auswahl invertieren");
        invert.addActionListener(e -> {
            java.util.Set<String> newHidden = new java.util.HashSet<String>();
            for (String k : kinds) {
                if (!hidden.contains(k)) newHidden.add(k);
            }
            hidden.clear();
            hidden.addAll(newHidden);
            if (isCallees) applyCalleesFilter(); else applyCallersFilter();
            persistHierarchyState();
        });
        popup.add(invert);
        popup.addSeparator();

        if (kinds.isEmpty()) {
            JMenuItem empty = new JMenuItem("(keine Typen verfügbar)");
            empty.setEnabled(false);
            popup.add(empty);
            return popup;
        }

        for (final String kind : kinds) {
            final JCheckBoxMenuItem item = new JCheckBoxMenuItem(
                    kindIcon(kind) + " " + kind, !hidden.contains(kind));
            item.addActionListener(e -> {
                if (item.isSelected()) hidden.remove(kind); else hidden.add(kind);
                if (isCallees) applyCalleesFilter(); else applyCallersFilter();
                persistHierarchyState();
            });
            popup.add(item);
        }
        return popup;
    }

    /** Small icon prefix matching the DependencyKind display labels (best-effort). */
    private static String kindIcon(String kind) {
        if (kind == null) return "";
        switch (kind) {
            case "CALLNAT":   return "📞";
            case "FETCH":     return "🚀";
            case "CALL":      return "⚙";
            case "PERFORM":   return "🔄";
            case "INCLUDE":   return "📎";
            case "USING":     return "📦";
            case "INPUT_MAP": return "🖥";
            case "VIEW":      return "📊";
            case "DB_ACCESS": return "🗄";
            default:          return "•";
        }
    }

    private static void updateFilterButtonStyle(JButton btn, java.util.Set<String> hidden) {
        if (btn == null) return;
        if (hidden.isEmpty()) {
            btn.setText("\uD83D\uDD3D Filter");
            btn.setForeground(UIManager.getColor("Button.foreground"));
        } else {
            btn.setText("\uD83D\uDD3D Filter (" + hidden.size() + ")");
            btn.setForeground(new Color(180, 90, 0));
        }
    }

    /** Build the small "Aa" name‑regex button for the callees/callers tab. */
    private JButton createNameRegexButton(final boolean isCallees) {
        final JButton btn = new JButton("\uD83D\uDD24"); // 🔤
        btn.setToolTipText("Dateinamen-Regex \u2014 filtert die absoluten Treffer nach Quelldatei");
        btn.setMargin(new Insets(1, 6, 1, 6));
        btn.setFocusable(false);
        btn.setFont(btn.getFont().deriveFont(11f));
        final de.bund.zrb.ui.util.RegexNameFilter model = isCallees ? calleesNameFilter : callersNameFilter;
        btn.addActionListener(e -> {
            final CallHierarchyData data = isCallees ? lastCalleesData : lastCallersData;
            JPopupMenu popup = model.buildPopupMenu(btn,
                    new de.bund.zrb.ui.util.RegexNameFilter.PrefixSupplier() {
                        @Override
                        public java.util.Collection<String> get() {
                            return collectHierarchyNames(data);
                        }
                    },
                    "die Quelldatei");
            popup.show(btn, 0, btn.getHeight());
        });
        return btn;
    }

    private static void updateNameRegexButtonStyle(JButton btn,
                                                   de.bund.zrb.ui.util.RegexNameFilter model) {
        if (btn == null || model == null) return;
        model.updateButtonStyle(btn, "\uD83D\uDD24", "\uD83D\uDD24*",
                "Dateinamen-Regex \u2014 filtert die absoluten Treffer nach Quelldatei");
    }

    /** Collect candidate names (source file or target object) from the entire hierarchy. */
    private static java.util.List<String> collectHierarchyNames(CallHierarchyData root) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        if (root != null) collectHierarchyNamesRecursive(root.getChildren(), out);
        return new java.util.ArrayList<String>(out);
    }

    private static void collectHierarchyNamesRecursive(List<CallHierarchyData> children,
                                                       java.util.Set<String> out) {
        for (CallHierarchyData c : children) {
            String n = extractName(c.getSourceFilePath());
            if (n == null) n = extractName(c.getTargetPath());
            if (n == null) n = c.getDisplayText();
            if (n != null && !n.isEmpty()) out.add(n);
            collectHierarchyNamesRecursive(c.getChildren(), out);
        }
    }

    private void setCalleesTabTitle(int visible, int total, boolean filtered) {
        setHierarchyTabTitle(TAB_IDX_CALLEES, TAB_TITLE_CALLEES, visible, total, filtered);
    }

    private void setCallersTabTitle(int visible, int total, boolean filtered) {
        setHierarchyTabTitle(TAB_IDX_CALLERS, TAB_TITLE_CALLERS, visible, total, filtered);
    }

    /** Legacy single-count setter (no filter): {@code count<0} → no parens. */
    private void setCalleesTabTitle(int count) {
        setHierarchyTabTitle(TAB_IDX_CALLEES, TAB_TITLE_CALLEES, count, count, false);
    }

    private void setCallersTabTitle(int count) {
        setHierarchyTabTitle(TAB_IDX_CALLERS, TAB_TITLE_CALLERS, count, count, false);
    }

    private void setHierarchyTabTitle(int idx, String base, int visible, int total, boolean filtered) {
        if (hierarchyTabs == null || hierarchyTabs.getTabCount() <= idx) return;
        String title;
        if (total < 0) {
            title = base;
        } else if (filtered) {
            title = base + " (" + visible + "/" + total + ")";
        } else {
            title = base + " (" + total + ")";
        }
        hierarchyTabs.setTitleAt(idx, title);
    }

    /**
     * Show a loading indicator in BOTH hierarchy tabs.
     */
    public void showCallHierarchyLoading() {
        showCalleesLoading();
        showCallersLoading();
    }

    public void showCalleesLoading() {
        lastCalleesData = null;
        lastCalleesObjectName = null;
        calleesRoot.removeAllChildren();
        calleesRoot.add(new DefaultMutableTreeNode("⏳ Lade Callees…"));
        calleesModel.reload();
        setCalleesTabTitle(-1);
        calleesStatusLabel.setText(" ");
    }

    public void showCallersLoading() {
        lastCallersData = null;
        lastCallersObjectName = null;
        callersRoot.removeAllChildren();
        callersRoot.add(new DefaultMutableTreeNode("⏳ Lade Caller…"));
        callersModel.reload();
        setCallersTabTitle(-1);
        callersStatusLabel.setText(" ");
    }

    /**
     * Clear BOTH hierarchy tabs.
     */
    public void clearCallHierarchy() {
        clearCallees();
        clearCallers();
    }

    public void clearCallees() {
        lastCalleesData = null;
        lastCalleesObjectName = null;
        calleesRoot.removeAllChildren();
        calleesModel.reload();
        setCalleesTabTitle(-1);
        calleesStatusLabel.setText(" ");
    }

    public void clearCallers() {
        lastCallersData = null;
        lastCallersObjectName = null;
        callersRoot.removeAllChildren();
        callersModel.reload();
        setCallersTabTitle(-1);
        callersStatusLabel.setText(" ");
    }

    /**
     * Show a placeholder message in BOTH hierarchy tabs.
     */
    public void showCallHierarchyPlaceholder(String message) {
        showCalleesPlaceholder(message);
        showCallersPlaceholder(message);
    }

    public void showCalleesPlaceholder(String message) {
        lastCalleesData = null;
        lastCalleesObjectName = null;
        calleesRoot.removeAllChildren();
        calleesRoot.add(new DefaultMutableTreeNode(message));
        calleesModel.reload();
        setCalleesTabTitle(-1);
        calleesStatusLabel.setText(" ");
    }

    public void showCallersPlaceholder(String message) {
        lastCallersData = null;
        lastCallersObjectName = null;
        callersRoot.removeAllChildren();
        callersRoot.add(new DefaultMutableTreeNode(message));
        callersModel.reload();
        setCallersTabTitle(-1);
        callersStatusLabel.setText(" ");
    }

    /**
     * Display a grouped hierarchy (e.g. Confluence ancestors/children) in the callees tab.
     * The callers tab is cleared since this view doesn't have a caller analogue.
     */
    public void updateCallHierarchyGrouped(String title, List<CallHierarchyData> groups) {
        calleesRoot.removeAllChildren();
        int totalNodes = 0;
        int directChildren = 0;
        if (groups != null) {
            for (CallHierarchyData group : groups) {
                String groupLabel = group.getDisplayText();
                DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupLabel);
                for (CallHierarchyData child : group.getChildren()) {
                    RelationEntry entry = new RelationEntry(
                            child.getDisplayText(), child.getTargetPath(),
                            child.isRecursive() ? "CALL_RECURSIVE" : "CONFLUENCE_HIERARCHY");
                    groupNode.add(new DefaultMutableTreeNode(entry));
                    totalNodes++;
                }
                calleesRoot.add(groupNode);
                directChildren++;
            }
        }
        if (calleesRoot.getChildCount() == 0) {
            calleesRoot.add(new DefaultMutableTreeNode("(keine Hierarchy verfügbar)"));
            directChildren = -1;
        }
        calleesModel.reload();
        setCalleesTabTitle(directChildren);
        calleesStatusLabel.setText(title != null ? title + " — " + totalNodes + " Einträge" : " ");
        for (int i = 0; i < calleesTree.getRowCount(); i++) {
            calleesTree.expandRow(i);
        }
        clearCallers();
    }

    /** Common click handler for both hierarchy trees. */
    private void handleHierarchyClick(JTree tree, MouseEvent e, boolean isCalleesTree) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(node.getUserObject() instanceof RelationEntry)) return;
        RelationEntry entry = (RelationEntry) node.getUserObject();

        // Default JTree selection (blue highlight) is applied automatically by Swing
        // when the user clicks — no custom rendering needed.

        if (e.getClickCount() == 1) {
            if (entry.getLineNumber() > 0) {
                if (onLineNavigateInFile != null) {
                    onLineNavigateInFile.accept(entry.getSourceFilePath(), entry.getLineNumber());
                } else if (onLineNavigate != null) {
                    onLineNavigate.accept(entry.getLineNumber());
                }
            } else if (entry.getTargetPath() != null && !entry.getTargetPath().isEmpty()
                    && onRelationOpen != null) {
                // No source line → treat single click as "open target" so URLs
                // (Confluence/Wiki links, NDV objects, …) navigate immediately
                // instead of requiring a double click.
                onRelationOpen.accept(entry);
            }
        }
        if (e.getClickCount() == 2 && onRelationOpen != null) {
            onRelationOpen.accept(entry);
        }
    }

    /**
     * Attach a right-click context menu offering "🌐 Im Browser anzeigen" to a
     * JTree containing {@link RelationEntry} user objects. The menu opens the
     * entry's {@code targetPath} via {@link java.awt.Desktop#browse} when the
     * path looks like an http(s) URL. Useful to force external-browser display
     * for internal Confluence/Wiki links that would otherwise open as in-app tabs.
     */
    private void installBrowserContextMenu(final JTree targetTree) {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem openInBrowser = new JMenuItem("🌐 Im Browser anzeigen");
        menu.add(openInBrowser);

        openInBrowser.addActionListener(ev -> {
            TreePath p = targetTree.getSelectionPath();
            if (p == null) return;
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) p.getLastPathComponent();
            if (!(n.getUserObject() instanceof RelationEntry)) return;
            String url = ((RelationEntry) n.getUserObject()).getTargetPath();
            if (url == null || url.isEmpty()) return;
            if (!url.startsWith("http://") && !url.startsWith("https://")) return;
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception ex) {
                // ignore — best effort
            }
        });

        targetTree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                TreePath path = targetTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                targetTree.setSelectionPath(path);
                Object userObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                if (!(userObj instanceof RelationEntry)) return;
                String url = ((RelationEntry) userObj).getTargetPath();
                openInBrowser.setEnabled(url != null
                        && (url.startsWith("http://") || url.startsWith("https://")));
                menu.show(targetTree, e.getX(), e.getY());
            }
        });
    }

    private void addHierarchyChildren(DefaultMutableTreeNode parent, List<CallHierarchyData> children) {
        for (CallHierarchyData child : children) {
            // Create a RelationEntry so single-click navigation works (incl. source file path
            // for nested entries whose line number lives in another file).
            // Preserve the original reference kind (CONFLUENCE_LINK / WIKI_LINK / …)
            // when present so MainFrame's open-handler can route correctly; fall
            // back to CALL_HIERARCHY for traditional Natural call trees.
            String type;
            if (child.isRecursive()) {
                type = "CALL_RECURSIVE";
            } else if (child.getReferenceKind() != null && !child.getReferenceKind().isEmpty()) {
                type = child.getReferenceKind();
            } else {
                type = "CALL_HIERARCHY";
            }
            RelationEntry entry = new RelationEntry(
                    child.getDisplayText(),
                    child.getTargetPath(),
                    type,
                    child.getLineNumber(),
                    child.getSourceFilePath(),
                    child.getReferenceKind()
            );
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
            if (!child.getChildren().isEmpty()) {
                addHierarchyChildren(node, child.getChildren());
            }
            parent.add(node);
        }
    }

    private int countNodes(CallHierarchyData node) {
        int count = 0;
        for (CallHierarchyData child : node.getChildren()) {
            count += 1 + countNodes(child);
        }
        return count;
    }


    /**
     * Data model for a single node in the call hierarchy tree (UI-agnostic).
     * Created by TabbedPaneManager from NaturalDependencyGraph.CallHierarchyNode.
     */
    public static class CallHierarchyData {
        private final String displayText;
        private final String targetPath;   // e.g. "ndv://LIB/OBJECT" — file to OPEN on double-click
        private final boolean recursive;
        private final int lineNumber;      // source line number for in-editor navigation (0 = unknown)
        /**
         * File that the {@link #lineNumber} refers to (e.g. "ndv://LIB/OBJECT").
         * For top-level callee children this is the currently open file; for nested
         * children it is the parent's source file. May be {@code null} → use current tab.
         */
        private final String sourceFilePath;
        /**
         * Reference kind code (e.g. "CALLNAT", "PERFORM", "INCLUDE", "USING", "VIEW", …)
         * used by the kind filter UI. May be {@code null} for non‑Natural sources or roots.
         */
        private final String referenceKind;
        private final List<CallHierarchyData> children;

        /** Backward-compatible constructor (lineNumber defaults to 0). */
        public CallHierarchyData(String displayText, String targetPath, boolean recursive,
                                 List<CallHierarchyData> children) {
            this(displayText, targetPath, recursive, 0, null, null, children);
        }

        public CallHierarchyData(String displayText, String targetPath, boolean recursive,
                                 int lineNumber, List<CallHierarchyData> children) {
            this(displayText, targetPath, recursive, lineNumber, null, null, children);
        }

        public CallHierarchyData(String displayText, String targetPath, boolean recursive,
                                 int lineNumber, String sourceFilePath,
                                 List<CallHierarchyData> children) {
            this(displayText, targetPath, recursive, lineNumber, sourceFilePath, null, children);
        }

        public CallHierarchyData(String displayText, String targetPath, boolean recursive,
                                 int lineNumber, String sourceFilePath, String referenceKind,
                                 List<CallHierarchyData> children) {
            this.displayText = displayText;
            this.targetPath = targetPath;
            this.recursive = recursive;
            this.lineNumber = lineNumber;
            this.sourceFilePath = sourceFilePath;
            this.referenceKind = referenceKind;
            this.children = children != null ? children : java.util.Collections.<CallHierarchyData>emptyList();
        }

        public String getDisplayText() { return displayText; }
        public String getTargetPath() { return targetPath; }
        public boolean isRecursive() { return recursive; }
        public int getLineNumber() { return lineNumber; }
        public String getSourceFilePath() { return sourceFilePath; }
        public String getReferenceKind() { return referenceKind; }
        public List<CallHierarchyData> getChildren() { return children; }
    }

    // ═══════════════════════════════════════════════════════════
    //  Bookmark API (unchanged)
    // ═══════════════════════════════════════════════════════════

    public void refreshBookmarks() {
        rootNode.removeAllChildren();
        List<BookmarkEntry> entries = BookmarkHelper.loadBookmarks();
        for (BookmarkEntry entry : entries) {
            rootNode.add(createNode(entry));
        }
        treeModel.reload();
        expandAll();
    }

    /**
     * Locate the bookmark node whose underlying {@link BookmarkEntry} has the given
     * prefixed path and select it in the tree. Used after creating a new bookmark
     * so the selection follows the new entry — that way subsequent bookmarks land
     * in the same folder (because {@link #resolveTargetBookmarkFolderLabel()}
     * follows the selection).
     */
    public void selectBookmarkByPath(String prefixedPath) {
        if (prefixedPath == null) return;
        TreePath found = findBookmarkPath(rootNode, prefixedPath);
        if (found != null) {
            tree.setSelectionPath(found);
            tree.scrollPathToVisible(found);
        }
    }

    private TreePath findBookmarkPath(DefaultMutableTreeNode node, String prefixedPath) {
        Object userObj = node.getUserObject();
        if (userObj instanceof BookmarkEntry) {
            BookmarkEntry e = (BookmarkEntry) userObj;
            if (!e.folder && prefixedPath.equals(e.path)) {
                return new TreePath(node.getPath());
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            TreePath p = findBookmarkPath(child, prefixedPath);
            if (p != null) return p;
        }
        return null;
    }

    public void setBookmarkForCurrentPath(Component parent, String path) {
        setBookmarkForCurrentPath(parent, path, null);
    }

    public void setBookmarkForCurrentPath(Component parent, String path, String backendType) {
        setBookmarkForCurrentPath(parent, path, backendType, "FILE");
    }

    public void setBookmarkForCurrentPath(Component parent, String path, String backendType, String resourceKind) {
        if (path == null || path.trim().isEmpty()) return;
        String prefixedPath = BookmarkEntry.buildPath(backendType, path);
        String label = new File(path).getName();
        if (label.isEmpty()) label = path;
        ensureGeneralFolder();
        BookmarkEntry entry = new BookmarkEntry(label, prefixedPath, false);
        entry.resourceKind = resourceKind != null ? resourceKind : "FILE";
        BookmarkHelper.addBookmarkToFolder(resolveTargetBookmarkFolderLabel(), entry);
        refreshBookmarks();
        selectBookmarkByPath(prefixedPath);
    }

    public boolean isBookmarked(String rawPath, String backendType) {
        if (rawPath == null) return false;
        String prefixedPath = BookmarkEntry.buildPath(backendType, rawPath);
        return isBookmarkedRecursive(BookmarkHelper.loadBookmarks(), prefixedPath);
    }

    public boolean toggleBookmark(String rawPath, String backendType) {
        return toggleBookmark(rawPath, backendType, "FILE");
    }

    public boolean toggleBookmark(String rawPath, String backendType, String resourceKind) {
        return toggleBookmark(rawPath, backendType, resourceKind, null);
    }

    public boolean toggleBookmark(String rawPath, String backendType, String resourceKind,
                                  de.bund.zrb.ui.NdvResourceState ndvState) {
        return toggleBookmark(rawPath, backendType, resourceKind, ndvState, null, null);
    }

    public boolean toggleBookmark(String rawPath, String backendType, String resourceKind,
                                  de.bund.zrb.ui.NdvResourceState ndvState, String tn3270MacroSteps) {
        return toggleBookmark(rawPath, backendType, resourceKind, ndvState, tn3270MacroSteps, null);
    }

    /**
     * @param displayLabel optional human-readable label (e.g. page title); if {@code null},
     *                     a label is derived from the path.
     */
    public boolean toggleBookmark(String rawPath, String backendType, String resourceKind,
                                  de.bund.zrb.ui.NdvResourceState ndvState, String tn3270MacroSteps,
                                  String displayLabel) {
        if (rawPath == null) return false;
        String prefixedPath = BookmarkEntry.buildPath(backendType, rawPath);
        if (isBookmarkedRecursive(BookmarkHelper.loadBookmarks(), prefixedPath)) {
            BookmarkHelper.removeBookmarkByPath(prefixedPath);
            refreshBookmarks();
            return false;
        } else {
            String label;
            if (displayLabel != null && !displayLabel.trim().isEmpty()) {
                // Explicit label provided (e.g. page title from Confluence/Wiki reader)
                label = displayLabel.trim();
            } else if (prefixedPath.startsWith(BookmarkEntry.SEARCH_PREFIX)) {
                // Search bookmark — use the query as label
                // rawPath here is already the full prefixed path; extract the query
                BookmarkEntry tmpEntry = new BookmarkEntry(null, prefixedPath, false);
                String query = tmpEntry.getSearchQuery();
                label = query != null && !query.isEmpty() ? query : rawPath;
            } else if ("BROWSER".equals(backendType)
                    && (rawPath.startsWith("http://") || rawPath.startsWith("https://"))) {
                // Use domain as label for browser bookmarks
                try {
                    java.net.URL url = new java.net.URL(rawPath);
                    label = url.getHost();
                    String path = url.getPath();
                    if (path != null && !path.isEmpty() && !"/".equals(path)) {
                        // Append path (truncated) for disambiguation
                        String shortPath = path.length() > 30 ? path.substring(0, 30) + "…" : path;
                        label = label + shortPath;
                    }
                } catch (java.net.MalformedURLException e) {
                    label = rawPath;
                }
            } else {
                label = new java.io.File(rawPath).getName();
                if (label.isEmpty()) label = rawPath;
            }
            ensureGeneralFolder();
            BookmarkEntry entry = new BookmarkEntry(label, prefixedPath, false);
            entry.resourceKind = resourceKind != null ? resourceKind : "FILE";
            if (ndvState != null && "FILE".equals(resourceKind)) {
                de.bund.zrb.ndv.NdvObjectInfo obj = ndvState.getObjectInfo();
                if (obj != null) {
                    entry.ndvLibrary = ndvState.getLibrary();
                    entry.ndvObjectName = obj.getName();
                    entry.ndvObjectType = obj.getType();
                    entry.ndvTypeExtension = obj.getTypeExtension();
                    entry.ndvDbid = obj.getDatabaseId();
                    entry.ndvFnr = obj.getFileNumber();
                }
            }
            if (tn3270MacroSteps != null && !tn3270MacroSteps.isEmpty()) {
                entry.tn3270MacroSteps = tn3270MacroSteps;
            }
            BookmarkHelper.addBookmarkToFolder(resolveTargetBookmarkFolderLabel(), entry);
            refreshBookmarks();
            selectBookmarkByPath(prefixedPath);
            return true;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal
    // ═══════════════════════════════════════════════════════════

    private boolean isBookmarkedRecursive(List<BookmarkEntry> entries, String prefixedPath) {
        for (BookmarkEntry e : entries) {
            if (!e.folder && prefixedPath.equals(e.path)) return true;
            if (e.folder && e.children != null) {
                if (isBookmarkedRecursive(e.children, prefixedPath)) return true;
            }
        }
        return false;
    }

    private void ensureGeneralFolder() {
        List<BookmarkEntry> bookmarks = BookmarkHelper.loadBookmarks();
        for (BookmarkEntry e : bookmarks) {
            if (e.folder && "Allgemein".equals(e.label)) return;
        }
        BookmarkHelper.addBookmark(new BookmarkEntry("Allgemein", null, true));
    }

    /**
     * Resolve the bookmark folder that newly created bookmarks should land in.
     * <ul>
     *   <li>If the user has selected a folder node in the bookmark tree, that folder
     *       is returned.</li>
     *   <li>If the user has selected a leaf (a real bookmark), the folder containing
     *       it is returned.</li>
     *   <li>Otherwise (nothing selected, or the invisible root selected), the default
     *       "Allgemein" folder is returned and created on demand.</li>
     * </ul>
     */
    public String resolveTargetBookmarkFolderLabel() {
        TreePath sel = tree.getSelectionPath();
        if (sel != null) {
            // Walk from the selected node up towards the root, returning the first
            // folder we encounter. This handles both "folder selected" and "leaf
            // selected → use its parent folder" naturally.
            for (int i = sel.getPathCount() - 1; i >= 0; i--) {
                Object pathComp = sel.getPathComponent(i);
                if (!(pathComp instanceof DefaultMutableTreeNode)) continue;
                Object userObj = ((DefaultMutableTreeNode) pathComp).getUserObject();
                if (userObj instanceof BookmarkEntry) {
                    BookmarkEntry entry = (BookmarkEntry) userObj;
                    if (entry.folder && entry.label != null && !entry.label.isEmpty()) {
                        return entry.label;
                    }
                }
            }
        }
        ensureGeneralFolder();
        return "Allgemein";
    }

    private void installMouseHandler() {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());

                if (SwingUtilities.isRightMouseButton(e)) {
                    if (selPath == null) {
                        showContextMenu(e.getComponent(), e.getX(), e.getY(), null);
                    } else {
                        Object nodeObj = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).getUserObject();
                        if (nodeObj instanceof BookmarkEntry) {
                            BookmarkEntry entry = (BookmarkEntry) nodeObj;
                            showContextMenu(e.getComponent(), e.getX(), e.getY(), entry);
                        }
                    }
                } else if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && selPath != null) {
                    Object nodeObj = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).getUserObject();
                    if (nodeObj instanceof BookmarkEntry) {
                        BookmarkEntry entry = (BookmarkEntry) nodeObj;
                        if (entry.isLeaf()) {
                            onBookmarkOpen.accept(entry);
                        }
                    }
                }
            }
        });
    }

    private void showContextMenu(Component invoker, int x, int y, BookmarkEntry entry) {
        JPopupMenu menu = new JPopupMenu();

        if (entry == null) {
            JMenuItem newFolder = new JMenuItem("📁 Neuer Ordner");
            newFolder.addActionListener(e -> {
                String name = JOptionPane.showInputDialog(invoker, "Name des neuen Ordners:");
                if (name != null && !name.trim().isEmpty()) {
                    BookmarkEntry folder = new BookmarkEntry(name.trim(), null, true);
                    BookmarkHelper.addBookmark(folder);
                    refreshBookmarks();
                }
            });
            menu.add(newFolder);
        } else {
            JMenuItem renameItem = new JMenuItem("✏ Umbenennen");
            renameItem.addActionListener(e -> {
                String newLabel = JOptionPane.showInputDialog(invoker, "Neuer Name:", entry.label);
                if (newLabel != null && !newLabel.trim().isEmpty()) {
                    if (entry.folder) {
                        BookmarkHelper.renameFolder(entry.label, newLabel.trim());
                    } else {
                        BookmarkHelper.renameBookmark(entry.path, newLabel.trim());
                    }
                    refreshBookmarks();
                }
            });
            menu.add(renameItem);

            if (entry.folder) {
                JMenuItem newSubfolderItem = new JMenuItem("📁 Neuen Unterordner anlegen");
                newSubfolderItem.addActionListener(e -> {
                    String name = JOptionPane.showInputDialog(invoker, "Name des neuen Ordners:");
                    if (name != null && !name.trim().isEmpty()) {
                        BookmarkEntry folder = new BookmarkEntry(name.trim(), null, true);
                        BookmarkHelper.addBookmarkToFolder(entry.label, folder);
                        refreshBookmarks();
                    }
                });
                menu.add(newSubfolderItem);
            }

            JMenuItem deleteItem = new JMenuItem("❌ Entfernen");
            deleteItem.addActionListener(e -> {
                if (entry.folder) {
                    BookmarkHelper.removeFolderByLabel(entry.label);
                } else {
                    BookmarkHelper.removeBookmarkByPath(entry.path);
                }
                refreshBookmarks();
            });
            menu.add(deleteItem);
        }

        if (entry != null && !entry.folder) {
            JMenuItem changePathItem = new JMenuItem("🛤 Pfad ändern");
            changePathItem.addActionListener(ev -> {
                String newPath = JOptionPane.showInputDialog(invoker, "Neuer Pfad:", entry.path);
                if (newPath != null && !newPath.trim().isEmpty()) {
                    BookmarkHelper.changeBookmarkPath(entry.path, newPath.trim());
                    refreshBookmarks();
                }
            });
            menu.add(changePathItem);
        }

        menu.show(invoker, x, y);
    }

    private DefaultMutableTreeNode createNode(BookmarkEntry entry) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
        if (entry.folder && entry.children != null) {
            for (BookmarkEntry child : entry.children) {
                node.add(createNode(child));
            }
        }
        return node;
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Relation entry model
    // ═══════════════════════════════════════════════════════════

    /**
     * A single relation entry displayed in the relations tree.
     * Used for wiki links, and later for program dependencies.
     */
    public static class RelationEntry {
        private final String label;
        private final String targetPath;  // e.g. "wiki://wikipedia_de/Seite" or later "ndv://LIB/OBJ"
        private final String type;        // "WIKI_LINK", "DEPENDENCY", "JCL_NAT_xxx", etc.
        private final int lineNumber;     // source line number for in-editor navigation (0 = unknown)
        /**
         * File that {@link #lineNumber} refers to (e.g. "ndv://LIB/OBJ"). May be {@code null}
         * → navigate within the currently selected tab. Used for nested call hierarchy
         * entries where the line number is located in another (parent) source file.
         */
        private final String sourceFilePath;
        /** Reference kind code (CALLNAT/PERFORM/INCLUDE/…) for type‑specific icons; nullable. */
        private final String referenceKind;

        public RelationEntry(String label, String targetPath, String type) {
            this(label, targetPath, type, 0, null, null);
        }

        public RelationEntry(String label, String targetPath, String type, int lineNumber) {
            this(label, targetPath, type, lineNumber, null, null);
        }

        public RelationEntry(String label, String targetPath, String type, int lineNumber,
                             String sourceFilePath) {
            this(label, targetPath, type, lineNumber, sourceFilePath, null);
        }

        public RelationEntry(String label, String targetPath, String type, int lineNumber,
                             String sourceFilePath, String referenceKind) {
            this.label = label;
            this.targetPath = targetPath;
            this.type = type;
            this.lineNumber = lineNumber;
            this.sourceFilePath = sourceFilePath;
            this.referenceKind = referenceKind;
        }

        public String getLabel() { return label; }
        public String getTargetPath() { return targetPath; }
        public String getType() { return type; }
        public int getLineNumber() { return lineNumber; }
        public String getSourceFilePath() { return sourceFilePath; }
        public String getReferenceKind() { return referenceKind; }

        @Override
        public String toString() {
            return label;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Renderers
    // ═══════════════════════════════════════════════════════════

    private static class BookmarkTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
        private final Icon fileIcon = UIManager.getIcon("FileView.fileIcon");

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();

            if (userObj instanceof BookmarkEntry) {
                BookmarkEntry entry = (BookmarkEntry) userObj;
                setText(entry.label);
                if (entry.folder) {
                    setToolTipText(null);
                    setIcon(folderIcon);
                } else {
                    String backend = entry.getBackendType();
                    String raw = entry.getRawPath();
                    if (entry.isSearch()) {
                        setToolTipText("[🔍 " + backend + "] " + raw);
                        String label = entry.label;
                        if (!label.startsWith("🔍")) {
                            setText("🔍 " + label);
                        }
                    } else {
                        setToolTipText("[" + backend + "] " + raw);
                    }
                    if ("BROWSER".equals(backend)) {
                        String label = entry.label;
                        if (!label.startsWith("🌐")) {
                            setText("🌐 " + label);
                        }
                    }
                    setIcon(fileIcon);
                }
            }

            return comp;
        }
    }

    /**
     * Renderer for the relations/dependencies and hierarchy trees.
     * Replaces the default folder/leaf icons with type‑specific emoji prefixes —
     * group nodes already carry their emoji in the label text, leaf entries get
     * a prefix derived from {@link RelationEntry#getReferenceKind()} or the
     * {@code type}. Natural programs / system functions keep their existing
     * green/blue badges.
     */
    private static class RelationTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final Color NAT_BG = new Color(34, 139, 34);  // forest green
        private static final Color SYSFUNC_FG = new Color(0, 100, 200);  // blue for system functions

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            // Remove the default Swing folder / leaf icons — we render everything via
            // emoji prefixes inside the label text (matches the Outline view convention).
            setIcon(null);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();

            if (userObj instanceof RelationEntry) {
                RelationEntry entry = (RelationEntry) userObj;
                String type = entry.getType();
                boolean isNatural = (type != null && type.startsWith("JCL_NAT_"))
                        || (entry.getTargetPath() != null && entry.getTargetPath().startsWith("nat-jcl://"));
                boolean isSysFunc = "JCL_SYSFUNC".equals(type)
                        || (entry.getTargetPath() != null && entry.getTargetPath().startsWith("sysfunc://"));
                if (isNatural) {
                    setFont(getFont().deriveFont(java.awt.Font.BOLD));
                    if (!sel) setForeground(NAT_BG);
                    String label = entry.getLabel();
                    if (!label.startsWith("🌿")) label = "🌿 " + label;
                    setText(label);
                    setToolTipText("Natural-Programm — Doppelklick zum Öffnen via NDV");
                } else if (isSysFunc) {
                    setFont(getFont().deriveFont(java.awt.Font.BOLD));
                    if (!sel) setForeground(SYSFUNC_FG);
                    String label = entry.getLabel();
                    if (!label.startsWith("🔗") && !label.startsWith("📖")) {
                        label = "📖 " + label;
                    }
                    setText(label);
                    setToolTipText("Systemfunktion — Doppelklick öffnet Wikipedia-Artikel");
                } else {
                    // Hierarchy / generic entry → prefix with kind icon
                    String kind = entry.getReferenceKind();
                    String label = entry.getLabel();
                    String prefix = kindIcon(kind);
                    if (prefix != null && !prefix.isEmpty()
                            && !startsWithEmojiPrefix(label, prefix)) {
                        label = prefix + " " + label;
                    }
                    setText(label);
                    if (type != null && type.startsWith("DEPENDENCY_")) {
                        setToolTipText(entry.getTargetPath());
                    } else if (kind != null) {
                        setToolTipText(kind + (entry.getTargetPath() != null
                                ? "  —  " + entry.getTargetPath() : ""));
                    }
                }
            }

            return comp;
        }

        private static boolean startsWithEmojiPrefix(String label, String prefix) {
            if (label == null) return false;
            return label.startsWith(prefix) || label.startsWith("🌿") || label.startsWith("📖");
        }
    }
}
