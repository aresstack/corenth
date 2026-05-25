package de.bund.zrb.ui.settings;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * {@link JPanel}-Spezialisierung, die {@link Scrollable} implementiert und der
 * umgebenden {@link javax.swing.JScrollPane} signalisiert, dass der Inhalt die
 * Viewport-<b>Breite</b> verfolgen soll. Damit reflowed der Inhalt automatisch
 * auf die aktuelle Dialogbreite und es entsteht <b>keine horizontale Scrollbar</b>
 * mehr — Felder werden enger gezeichnet, HTML-Labels brechen um, anstatt nach
 * rechts aus dem sichtbaren Bereich zu wandern.
 *
 * <p>Die Höhe wird absichtlich <b>nicht</b> ans Viewport gebunden, damit lange
 * Formulare weiterhin vertikal scrollen können.</p>
 */
final class ScrollableFormPanel extends JPanel implements Scrollable {

    ScrollableFormPanel(LayoutManager layout) {
        super(layout);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? 16 : 32;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    /** Inhalt reflowed auf Viewport-Breite → keine horizontale Scrollbar. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /** Inhalt kann höher als das Viewport sein → vertikale Scrollbar erlaubt. */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

