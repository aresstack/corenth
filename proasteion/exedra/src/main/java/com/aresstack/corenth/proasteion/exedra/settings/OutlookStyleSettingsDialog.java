package com.aresstack.corenth.proasteion.exedra.settings;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic Outlook-style settings dialog.
 * <ul>
 *   <li>Left: category navigation ({@link JList})</li>
 *   <li>Right: card-based content panel – each card has its own {@link JScrollPane}</li>
 *   <li>Bottom: footer with left action-buttons and right Apply/OK/Cancel</li>
 * </ul>
 */
public class OutlookStyleSettingsDialog extends JDialog {

    private final List<SettingsCategory> categories = new ArrayList<>();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> categoryList;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardContainer;
    private boolean applied = false;

    /**
     * @param owner       parent window (may be null)
     * @param title       dialog title
     * @param categories  ordered list of categories
     * @param leftButtons optional buttons for the footer-left area (may be empty/null)
     */
    public OutlookStyleSettingsDialog(Window owner,
                                      String title,
                                      List<SettingsCategory> categories,
                                      List<JButton> leftButtons) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        for (SettingsCategory cat : categories) {
            this.categories.add(cat);
            listModel.addElement(cat.getTitle());
        }

        // ---- left navigation ----
        categoryList = new JList<>(listModel);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setFixedCellHeight(28);
        categoryList.setVisibleRowCount(Math.min(14, categories.size()));
        categoryList.setBorder(new EmptyBorder(4, 4, 4, 8));
        categoryList.setFont(categoryList.getFont().deriveFont(Font.PLAIN, 13f));

        JScrollPane leftScroll = new JScrollPane(categoryList);
        leftScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        leftScroll.setMinimumSize(new Dimension(160, 100));

        // ---- right content ----
        cardContainer = new JPanel(cardLayout);
        for (SettingsCategory cat : categories) {
            JComponent comp = cat.getComponent();
            JScrollPane scrollPane = wrapInScrollPane(comp);
            cardContainer.add(scrollPane, cat.getId());
        }

        // ---- selection listener ----
        categoryList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = categoryList.getSelectedIndex();
            if (idx >= 0 && idx < this.categories.size()) {
                cardLayout.show(cardContainer, this.categories.get(idx).getId());
            }
        });

        // ---- footer ----
        JPanel footer = new JPanel(new BorderLayout());
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        if (leftButtons != null) {
            for (JButton btn : leftButtons) leftPanel.add(btn);
        }

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton btnApply = new JButton("Apply");
        JButton btnOk = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");

        btnApply.addActionListener(e -> doApply());
        btnOk.addActionListener(e -> { if (doApply()) dispose(); });
        btnCancel.addActionListener(e -> dispose());

        rightPanel.add(btnApply);
        rightPanel.add(btnOk);
        rightPanel.add(btnCancel);

        footer.add(leftPanel, BorderLayout.WEST);
        footer.add(rightPanel, BorderLayout.EAST);

        // ---- assemble ----
        getContentPane().setLayout(new BorderLayout(8, 0));
        getContentPane().add(leftScroll, BorderLayout.WEST);
        getContentPane().add(cardContainer, BorderLayout.CENTER);
        getContentPane().add(footer, BorderLayout.SOUTH);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = clamp((int) (screen.width * 0.6), 700, 1200);
        int h = clamp((int) (screen.height * 0.7), 500, 900);
        setSize(w, h);
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(owner);

        if (!categories.isEmpty()) {
            categoryList.setSelectedIndex(0);
        }
    }

    /** Select a category by index (0-based). */
    public void selectCategory(int index) {
        if (index >= 0 && index < categories.size()) {
            categoryList.setSelectedIndex(index);
        }
    }

    /** Whether Apply was invoked at least once. */
    public boolean wasApplied() {
        return applied;
    }

    private boolean doApply() {
        for (SettingsCategory cat : categories) {
            try {
                cat.validate();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        for (SettingsCategory cat : categories) {
            cat.apply();
        }
        applied = true;
        return true;
    }

    private static JScrollPane wrapInScrollPane(JComponent comp) {
        if (comp instanceof JScrollPane) return (JScrollPane) comp;
        JScrollPane sp = new JScrollPane(comp);
        sp.setBorder(new EmptyBorder(0, 0, 0, 0));
        return sp;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
