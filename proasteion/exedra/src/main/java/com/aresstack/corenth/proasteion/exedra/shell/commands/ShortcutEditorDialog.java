package com.aresstack.corenth.proasteion.exedra.shell.commands;

import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;
import com.aresstack.corenth.proasteion.exedra.command.ShortcutRegistry;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal shortcut editor dialog.
 * Displays all registered commands and their current shortcut bindings.
 * Users can select a row and press a key combination to assign a new shortcut.
 */
public final class ShortcutEditorDialog extends JDialog {

    private final ShortcutRegistry shortcutRegistry;
    private final CommandRegistry commandRegistry;
    private final List<Row> rows = new ArrayList<>();
    private final ShortcutTableModel tableModel;
    private boolean applied = false;

    public ShortcutEditorDialog(JFrame owner, ShortcutRegistry shortcutRegistry,
                                 CommandRegistry commandRegistry) {
        super(owner, "Keyboard Shortcuts", true);
        this.shortcutRegistry = shortcutRegistry;
        this.commandRegistry = commandRegistry;

        // Build row data
        for (ShellCommand cmd : commandRegistry.getAll()) {
            KeyStroke effective = shortcutRegistry.resolve(cmd);
            rows.add(new Row(cmd.getId(), cmd.getLabel(), effective));
        }

        tableModel = new ShortcutTableModel();
        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);

        // Shortcut capture field
        JTextField captureField = new JTextField(20);
        captureField.setEditable(false);
        captureField.setBorder(BorderFactory.createTitledBorder("Press shortcut key"));
        captureField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    captureField.setText("");
                    return;
                }
                int modifiers = e.getModifiers();
                if (e.getKeyCode() == KeyEvent.VK_SHIFT || e.getKeyCode() == KeyEvent.VK_CONTROL
                        || e.getKeyCode() == KeyEvent.VK_ALT || e.getKeyCode() == KeyEvent.VK_META) {
                    return; // Wait for non-modifier key
                }
                KeyStroke ks = KeyStroke.getKeyStroke(e.getKeyCode(), modifiers);
                captureField.setText(ks.toString());

                int row = table.getSelectedRow();
                if (row >= 0) {
                    rows.get(row).keyStroke = ks;
                    tableModel.fireTableRowsUpdated(row, row);
                }
                e.consume();
            }
        });

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                rows.get(row).keyStroke = null;
                tableModel.fireTableRowsUpdated(row, row);
                captureField.setText("");
            }
        });

        JPanel capturePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        capturePanel.add(new JLabel("Select command, then press key:"));
        capturePanel.add(captureField);
        capturePanel.add(clearBtn);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyBtn = new JButton("Apply");
        JButton cancelBtn = new JButton("Cancel");
        applyBtn.addActionListener(e -> { applyShortcuts(); applied = true; dispose(); });
        cancelBtn.addActionListener(e -> dispose());
        buttonPanel.add(applyBtn);
        buttonPanel.add(cancelBtn);

        setLayout(new BorderLayout(8, 8));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(capturePanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.SOUTH);
        setSize(550, 400);
        setLocationRelativeTo(owner);
    }

    public boolean wasApplied() { return applied; }

    private void applyShortcuts() {
        shortcutRegistry.clear();
        for (Row row : rows) {
            if (row.keyStroke != null) {
                shortcutRegistry.set(row.commandId, row.keyStroke);
            }
        }
    }

    private final class ShortcutTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Command", "Shortcut"};

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            Row r = rows.get(row);
            if (col == 0) return r.label;
            return r.keyStroke != null ? r.keyStroke.toString() : "";
        }
    }

    private static final class Row {
        final String commandId;
        final String label;
        KeyStroke keyStroke;

        Row(String commandId, String label, KeyStroke keyStroke) {
            this.commandId = commandId;
            this.label = label;
            this.keyStroke = keyStroke;
        }
    }
}
