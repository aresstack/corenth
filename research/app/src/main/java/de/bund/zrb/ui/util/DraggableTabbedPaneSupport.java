package de.bund.zrb.ui.util;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * IntelliJ-style drag-and-drop support for tabs across a group of
 * {@link JTabbedPane}s. Once a pane is registered via {@link #install(JTabbedPane)}
 * the user can:
 * <ul>
 *   <li>Drag a tab title to a different position within the same pane to reorder.</li>
 *   <li>Drag a tab title onto another registered pane (e.g. between the left and
 *       right tool windows) to move the tab into that pane.</li>
 * </ul>
 * The component, title, icon and tooltip are transferred 1:1 — the receiving pane
 * adopts the tab as if it had always lived there. Only panes registered with this
 * helper participate; arbitrary other panes (e.g. the main content area) are
 * not affected and silently ignore drops.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Drag is started after a small movement threshold to keep ordinary clicks
 *       (tab selection) working.</li>
 *   <li>The data flavor is a process-local Java object reference (no serialization),
 *       so cross-JVM drag is not supported (and not needed for in-app tool windows).</li>
 * </ul>
 */
public final class DraggableTabbedPaneSupport {

    /** Mouse-pixel threshold before a drag actually starts (avoids accidental drags). */
    private static final int DRAG_THRESHOLD = 5;

    /**
     * Process-local data flavor referencing a {@link TabRef}. Created lazily because
     * the {@link DataFlavor} constructor declares a checked exception.
     */
    private static final DataFlavor TAB_FLAVOR = makeFlavor();

    /** Panes that may exchange tabs with each other. */
    private static final Set<JTabbedPane> REGISTRY = Collections.newSetFromMap(
            new java.util.WeakHashMap<JTabbedPane, Boolean>());

    /** Marker client property holding the index pressed by the user. */
    private static final String CLIENT_KEY_SOURCE_INDEX = "draggableTabs.sourceIndex";

    private DraggableTabbedPaneSupport() { /* static utility */ }

    /** Register a tabbed pane to participate in cross-pane tab drag-and-drop. */
    public static void install(final JTabbedPane pane) {
        if (pane == null || TAB_FLAVOR == null) return;
        if (REGISTRY.contains(pane)) return;
        REGISTRY.add(pane);

        pane.setTransferHandler(new TabTransferHandler());

        MouseAdapter press = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    pane.putClientProperty(CLIENT_KEY_SOURCE_INDEX, null);
                    return;
                }
                int idx = pane.indexAtLocation(e.getX(), e.getY());
                if (idx < 0) {
                    pane.putClientProperty(CLIENT_KEY_SOURCE_INDEX, null);
                    return;
                }
                pane.putClientProperty(CLIENT_KEY_SOURCE_INDEX, Integer.valueOf(idx));
                pane.putClientProperty(CLIENT_KEY_SOURCE_INDEX + ".pressPoint", e.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Cleared by mousePressed of the next interaction; nothing else to do.
            }
        };
        MouseMotionAdapter motion = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Object idxObj = pane.getClientProperty(CLIENT_KEY_SOURCE_INDEX);
                Object pressPt = pane.getClientProperty(CLIENT_KEY_SOURCE_INDEX + ".pressPoint");
                if (!(idxObj instanceof Integer) || !(pressPt instanceof Point)) return;
                Point p0 = (Point) pressPt;
                if (p0.distance(e.getPoint()) < DRAG_THRESHOLD) return;
                TransferHandler th = pane.getTransferHandler();
                if (th == null) return;
                th.exportAsDrag(pane, e, TransferHandler.MOVE);
                // Consume "press point" so a follow-up move on same press doesn't re-fire.
                pane.putClientProperty(CLIENT_KEY_SOURCE_INDEX + ".pressPoint", null);
            }
        };
        pane.addMouseListener(press);
        pane.addMouseMotionListener(motion);
    }

    // ──────────────────────────────────────────────────────────
    //  Internals
    // ──────────────────────────────────────────────────────────

    private static DataFlavor makeFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=" + TabRef.class.getName());
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    /** Snapshot of the dragged tab. */
    private static final class TabRef {
        final JTabbedPane source;
        final Component component;
        final String title;
        final Icon icon;
        final String tooltip;
        final Component tabComponent; // custom tab renderer, if any
        final boolean enabled;

        TabRef(JTabbedPane src, int idx) {
            this.source = src;
            this.component = src.getComponentAt(idx);
            this.title = src.getTitleAt(idx);
            this.icon = src.getIconAt(idx);
            this.tooltip = src.getToolTipTextAt(idx);
            this.tabComponent = src.getTabComponentAt(idx);
            this.enabled = src.isEnabledAt(idx);
        }
    }

    private static final class TabTransferable implements Transferable {
        private final TabRef ref;

        TabTransferable(TabRef ref) { this.ref = ref; }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { TAB_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return TAB_FLAVOR != null && TAB_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return ref;
        }
    }

    private static final class TabTransferHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            if (!(c instanceof JTabbedPane)) return null;
            JTabbedPane pane = (JTabbedPane) c;
            Object idxObj = pane.getClientProperty(CLIENT_KEY_SOURCE_INDEX);
            if (!(idxObj instanceof Integer)) return null;
            int idx = (Integer) idxObj;
            if (idx < 0 || idx >= pane.getTabCount()) return null;
            return new TabTransferable(new TabRef(pane, idx));
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (TAB_FLAVOR == null) return false;
            if (!support.isDataFlavorSupported(TAB_FLAVOR)) return false;
            Component target = support.getComponent();
            return target instanceof JTabbedPane && REGISTRY.contains(target);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            JTabbedPane target = (JTabbedPane) support.getComponent();
            try {
                TabRef ref = (TabRef) support.getTransferable().getTransferData(TAB_FLAVOR);
                if (ref == null) return false;

                // Locate source tab — original index might be stale after removals.
                JTabbedPane src = ref.source;
                int srcIdx = src.indexOfComponent(ref.component);
                if (srcIdx < 0) return false;

                // Compute target index from drop point.
                int targetIdx;
                if (support.isDrop()) {
                    Point dropPoint = support.getDropLocation().getDropPoint();
                    targetIdx = target.indexAtLocation(dropPoint.x, dropPoint.y);
                    if (targetIdx < 0) {
                        // Dropped on the body or empty header → append.
                        targetIdx = target.getTabCount();
                    } else {
                        // Decide left-of vs right-of based on x within the tab bounds.
                        Rectangle r = target.getBoundsAt(targetIdx);
                        if (r != null && dropPoint.x > r.x + r.width / 2) {
                            targetIdx++;
                        }
                    }
                } else {
                    targetIdx = target.getTabCount();
                }

                // Remove from source and adjust index if same-pane reorder.
                src.remove(srcIdx);
                if (src == target && srcIdx < targetIdx) {
                    targetIdx--;
                }
                if (targetIdx < 0) targetIdx = 0;
                if (targetIdx > target.getTabCount()) targetIdx = target.getTabCount();

                target.insertTab(ref.title, ref.icon, ref.component, ref.tooltip, targetIdx);
                if (ref.tabComponent != null) {
                    target.setTabComponentAt(targetIdx, ref.tabComponent);
                }
                target.setEnabledAt(targetIdx, ref.enabled);
                target.setSelectedIndex(targetIdx);
                // Persist new tab location (pane id + index) for every registered tool tab.
                ToolTabRegistry.onLayoutChanged();
                return true;
            } catch (UnsupportedFlavorException | IOException ex) {
                return false;
            } catch (RuntimeException ex) {
                return false;
            }
        }
    }
}

