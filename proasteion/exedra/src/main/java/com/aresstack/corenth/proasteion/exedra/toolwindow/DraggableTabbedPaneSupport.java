package com.aresstack.corenth.proasteion.exedra.toolwindow;

import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs drag-and-drop support between JTabbedPanes so that tabs
 * can be moved between the four tool-window areas.
 *
 * <p>Usage: call {@link #install(JTabbedPane...)} with all panes that
 * should participate in tab dragging.
 */
public final class DraggableTabbedPaneSupport {

    private static final List<JTabbedPane> participants = new ArrayList<>();
    private static final DataFlavor TAB_FLAVOR = DataFlavor.stringFlavor;

    private DraggableTabbedPaneSupport() { }

    /** Install drag-and-drop on the given panes. */
    public static void install(JTabbedPane... panes) {
        for (JTabbedPane pane : panes) {
            if (participants.contains(pane)) continue;
            participants.add(pane);
            installDragSource(pane);
            installDropTarget(pane);
        }
    }

    private static void installDragSource(final JTabbedPane pane) {
        DragSource ds = new DragSource();
        ds.createDefaultDragGestureRecognizer(pane, DnDConstants.ACTION_MOVE, new DragGestureListener() {
            @Override
            public void dragGestureRecognized(DragGestureEvent dge) {
                int idx = pane.getSelectedIndex();
                if (idx < 0) return;
                String transferId = System.identityHashCode(pane) + ":" + idx;
                dge.startDrag(null, new StringSelection(transferId), new DragSourceAdapter() { });
            }
        });
    }

    private static void installDropTarget(final JTabbedPane targetPane) {
        new DropTarget(targetPane, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                    Transferable t = dtde.getTransferable();
                    String data = (String) t.getTransferData(TAB_FLAVOR);
                    String[] parts = data.split(":");
                    int sourceHash = Integer.parseInt(parts[0]);
                    int sourceIdx = Integer.parseInt(parts[1]);

                    JTabbedPane sourcePane = findByHash(sourceHash);
                    if (sourcePane == null || sourcePane == targetPane) {
                        dtde.dropComplete(false);
                        return;
                    }

                    Component comp = sourcePane.getComponentAt(sourceIdx);
                    String title = sourcePane.getTitleAt(sourceIdx);
                    javax.swing.Icon icon = sourcePane.getIconAt(sourceIdx);

                    sourcePane.removeTabAt(sourceIdx);
                    targetPane.addTab(title, icon, comp);
                    targetPane.setSelectedComponent(comp);

                    dtde.dropComplete(true);
                } catch (Exception e) {
                    dtde.dropComplete(false);
                }
            }
        });
    }

    private static JTabbedPane findByHash(int hash) {
        for (JTabbedPane p : participants) {
            if (System.identityHashCode(p) == hash) return p;
        }
        return null;
    }
}
