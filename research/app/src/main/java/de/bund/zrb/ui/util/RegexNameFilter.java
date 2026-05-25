package de.bund.zrb.ui.util;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reusable name-based regex filter component shared between the NDV Connection Tab
 * (object list) and the Hierarchy tabs in {@link de.bund.zrb.ui.drawer.LeftDrawer}
 * (Callers/Callees). The filter keeps two coupled representations of the same
 * predicate:
 * <ul>
 *   <li>A free-form regex pattern (case insensitive).</li>
 *   <li>A set of three-letter prefix checkboxes the user can click — selecting
 *       any of them rebuilds the regex as an anchored alternation like
 *       {@code ^(\QABC\E|\QXYZ\E)}.</li>
 * </ul>
 * The class is UI-agnostic (state + matching) but also ships a {@link #buildPopupMenu}
 * helper to render the standard "Filter" popup with a scrollable prefix list, so the
 * exact same UX is used everywhere.
 */
public class RegexNameFilter {

    /** Maximum visible height for the scrollable prefix list inside the popup. */
    private static final int PREFIX_LIST_MAX_HEIGHT = 360;

    private String regex = "";
    private Pattern compiled = null;
    private final LinkedHashSet<String> selectedPrefixes = new LinkedHashSet<String>();
    private final List<Runnable> changeListeners = new ArrayList<Runnable>();
    /**
     * Master enable flag. When {@code false}, {@link #matches(String)} always returns
     * {@code true} regardless of the compiled regex — so the user can temporarily
     * suspend the filter via the popup's "Aktiviert" checkbox without losing the
     * configured pattern.
     */
    private boolean enabled = true;

    // ──────────────────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────────────────

    /** @return true if a non-empty regex is currently active. */
    public boolean isActive() {
        return enabled && compiled != null;
    }

    /** @return whether the filter is enabled at all (master switch). */
    public boolean isEnabled() {
        return enabled;
    }

    /** Toggle the master enable switch. Fires change listeners on transition. */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        fireChanged();
    }

    /** @return the raw regex source (may be empty). */
    public String getRegex() {
        return regex == null ? "" : regex;
    }

    /** @return mutable view of the currently checked 3-letter prefixes. */
    public Set<String> getSelectedPrefixes() {
        return selectedPrefixes;
    }

    /**
     * Replace the regex with a manually entered pattern. Clears the prefix selection
     * because manual editing decouples from the checkboxes. Fires change listeners.
     */
    public void setManualRegex(String pattern) {
        this.regex = pattern == null ? "" : pattern.trim();
        this.selectedPrefixes.clear();
        recompile();
        fireChanged();
    }

    /**
     * Replace the selected prefix set (e.g. when restoring persisted state). The
     * regex is rebuilt from the prefixes. Fires change listeners.
     */
    public void setSelectedPrefixes(Collection<String> prefixes) {
        this.selectedPrefixes.clear();
        if (prefixes != null) {
            for (String p : prefixes) {
                if (p == null) continue;
                String trimmed = p.trim().toUpperCase();
                if (!trimmed.isEmpty()) this.selectedPrefixes.add(trimmed);
            }
        }
        rebuildRegexFromPrefixes();
        fireChanged();
    }

    /**
     * Restore both the raw pattern and the prefix selection from persisted state
     * without firing listeners (typical use during construction). The raw pattern
     * is only honoured if the prefix selection is empty (prefixes take precedence
     * since they are what the UI shows checked).
     */
    public void restoreState(String savedRegex, Collection<String> savedPrefixes) {
        this.selectedPrefixes.clear();
        if (savedPrefixes != null) {
            for (String p : savedPrefixes) {
                if (p == null) continue;
                String trimmed = p.trim().toUpperCase();
                if (!trimmed.isEmpty()) this.selectedPrefixes.add(trimmed);
            }
        }
        if (!this.selectedPrefixes.isEmpty()) {
            rebuildRegexFromPrefixes();
        } else {
            this.regex = savedRegex == null ? "" : savedRegex.trim();
            recompile();
        }
    }

    /** Reset to "no filter". */
    public void clear() {
        this.regex = "";
        this.compiled = null;
        this.selectedPrefixes.clear();
        fireChanged();
    }

    /**
     * @return {@code true} if the filter is empty (no regex set) OR the name matches.
     *         A {@code null} name fails an active filter (defensive default).
     *         Always returns {@code true} when the master switch is disabled.
     */
    public boolean matches(String name) {
        if (!enabled || compiled == null) return true;
        if (name == null) return false;
        return compiled.matcher(name).find();
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null) changeListeners.add(listener);
    }

    // ──────────────────────────────────────────────────────────
    //  UI helpers
    // ──────────────────────────────────────────────────────────

    /**
     * Build the standard filter popup menu. Convenience overload using 3-letter prefixes
     * and no master "Aktiviert" toggle (NDV / hierarchy behaviour).
     */
    public JPopupMenu buildPopupMenu(final Component anchor,
                                     final PrefixSupplier prefixSupplier,
                                     final String targetLabel) {
        return buildPopupMenu(anchor, prefixSupplier, targetLabel, false, false);
    }

    /**
     * Build the filter popup menu.
     *
     * @param anchor          parent component for the edit dialog
     * @param prefixSupplier  candidate values (sorted/deduped; uppercased; truncated
     *                        to 3 chars unless {@code fullNames} is true)
     * @param targetLabel     short label used in the edit dialog (e.g. "Objektnamen", "Owner")
     * @param fullNames       if {@code true}, the candidate values are used verbatim
     *                        (full names) instead of being truncated to the first 3 chars
     * @param showEnableToggle if {@code true}, a leading "Aktiviert" master checkbox
     *                        is shown that toggles {@link #setEnabled(boolean)}
     */
    public JPopupMenu buildPopupMenu(final Component anchor,
                                     final PrefixSupplier prefixSupplier,
                                     final String targetLabel,
                                     final boolean fullNames,
                                     final boolean showEnableToggle) {
        JPopupMenu popup = new JPopupMenu();

        if (showEnableToggle) {
            final JCheckBoxMenuItem enableItem = new JCheckBoxMenuItem(
                    "Aktiviert", enabled);
            enableItem.addActionListener(ev -> setEnabled(enableItem.isSelected()));
            popup.add(enableItem);
        }

        JMenuItem info = new JMenuItem(regex.isEmpty()
                ? "(kein Regex aktiv)"
                : "Aktuell: " + regex);
        info.setEnabled(false);
        popup.add(info);
        popup.addSeparator();

        JMenuItem edit = new JMenuItem("\u270F Pattern bearbeiten\u2026");
        edit.addActionListener(ev -> {
            String input = (String) JOptionPane.showInputDialog(
                    anchor,
                    "Regex (wird auf " + (targetLabel != null ? targetLabel : "den Namen")
                            + " angewendet, case-insensitive):",
                    "Filter-Regex",
                    JOptionPane.PLAIN_MESSAGE,
                    null, null, regex);
            if (input == null) return;
            setManualRegex(input);
        });
        popup.add(edit);

        JMenuItem clearItem = new JMenuItem("\u274C Filter zur\u00fccksetzen");
        clearItem.setEnabled(!regex.isEmpty() || !selectedPrefixes.isEmpty());
        clearItem.addActionListener(ev -> clear());
        popup.add(clearItem);
        popup.addSeparator();

        // Collect candidate values
        SortedSet<String> prefixes = new TreeSet<String>();
        Collection<String> supplied = prefixSupplier != null ? prefixSupplier.get() : null;
        if (supplied != null) {
            for (String p : supplied) {
                if (p == null) continue;
                String trimmed = p.trim().toUpperCase();
                if (trimmed.isEmpty()) continue;
                if (fullNames) {
                    prefixes.add(trimmed);
                } else if (trimmed.length() >= 3) {
                    prefixes.add(trimmed.substring(0, 3));
                } else {
                    prefixes.add(trimmed);
                }
            }
        }

        if (prefixes.isEmpty()) {
            JMenuItem empty = new JMenuItem("(keine Eintr\u00e4ge verf\u00fcgbar)");
            empty.setEnabled(false);
            popup.add(empty);
            return popup;
        }

        JMenuItem hdr = new JMenuItem(fullNames
                ? "Werte ankreuzen:"
                : "Pr\u00e4fixe (erste 3 Buchstaben) ankreuzen:");
        hdr.setEnabled(false);
        popup.add(hdr);

        // Scrollable list of checkboxes — JCheckBoxMenuItems inside a JScrollPane don't
        // work reliably, so use a JPanel of plain JCheckBox-es. We intentionally keep
        // the popup open after each click so the user can multi-select quickly.
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(UIManager.getColor("MenuItem.background"));
        for (final String prefix : prefixes) {
            final JCheckBox cb = new JCheckBox(prefix, selectedPrefixes.contains(prefix));
            cb.setBackground(listPanel.getBackground());
            cb.setFocusable(false);
            cb.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            cb.addActionListener(ev -> {
                if (cb.isSelected()) selectedPrefixes.add(prefix);
                else selectedPrefixes.remove(prefix);
                rebuildRegexFromPrefixes();
                fireChanged();
            });
            listPanel.add(cb);
        }

        JScrollPane scroll = new JScrollPane(listPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        // Constrain height — the popup auto-sizes to its components otherwise.
        Dimension natural = listPanel.getPreferredSize();
        int width = Math.max(natural.width + 24, 120);
        int height = Math.min(natural.height + 4, PREFIX_LIST_MAX_HEIGHT);
        scroll.setPreferredSize(new Dimension(width, height));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        popup.add(scroll);

        return popup;
    }

    /** Apply the standard "active=orange" style to a filter button. */
    public void updateButtonStyle(JButton btn, String inactiveText, String activeText,
                                  String inactiveTooltip) {
        if (btn == null) return;
        if (!isActive()) {
            btn.setText(inactiveText);
            btn.setForeground(UIManager.getColor("Button.foreground"));
            btn.setToolTipText(inactiveTooltip);
        } else {
            btn.setText(activeText);
            btn.setForeground(new Color(180, 90, 0));
            btn.setToolTipText("Regex aktiv: " + regex);
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Internals
    // ──────────────────────────────────────────────────────────

    private void rebuildRegexFromPrefixes() {
        if (selectedPrefixes.isEmpty()) {
            regex = "";
        } else {
            StringBuilder sb = new StringBuilder("^(");
            boolean first = true;
            for (String p : selectedPrefixes) {
                if (!first) sb.append('|');
                sb.append(Pattern.quote(p));
                first = false;
            }
            sb.append(')');
            regex = sb.toString();
        }
        recompile();
    }

    private void recompile() {
        if (regex == null || regex.isEmpty()) {
            compiled = null;
            return;
        }
        try {
            compiled = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ex) {
            compiled = null;
        }
    }

    private void fireChanged() {
        for (Runnable r : changeListeners) {
            try { r.run(); } catch (RuntimeException ignored) { /* keep listeners independent */ }
        }
    }

    /** Functional interface so we can stay on Java 8 without {@code Supplier} import noise. */
    public interface PrefixSupplier {
        Collection<String> get();
    }

    /** Helper: split a comma-separated persisted prefix list. */
    public static List<String> splitCsv(String csv) {
        if (csv == null || csv.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Helper: join a prefix set for persistence. */
    public static String joinCsv(Collection<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String v : values) {
            if (!first) sb.append(',');
            sb.append(v);
            first = false;
        }
        return sb.toString();
    }
}

