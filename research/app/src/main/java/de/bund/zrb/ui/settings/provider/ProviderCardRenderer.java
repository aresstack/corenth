package de.bund.zrb.ui.settings.provider;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Generischer Renderer: erzeugt aus einer {@link ProviderDef} und einem
 * {@link Facet}-Set ein vollständiges Karten-Panel inkl. Lade-/Speicherlogik.
 *
 * <p>Eine einzige Methode pro Feldtyp und Slot-Typ — damit verschwindet
 * Provider-spezifischer Spaghetti-Code aus {@code ProviderConfigPanel} und
 * (perspektivisch) {@code AiSettingsPanel}.</p>
 */
public final class ProviderCardRenderer {

    private ProviderCardRenderer() {}

    /**
     * Ergebnis eines Render-Aufrufs: das fertige Panel plus zwei Adapter-Funktionen
     * zum Laden/Speichern gegen eine flache {@code Map<String,String>}.
     */
    public static final class RenderedCard {
        private final JPanel panel;
        private final List<Binding> bindings;
        private final Map<String, JComponent> componentsByKey;
        private final Map<String, JPanel> rowsByKey;
        private final Map<Facet, List<JComponent>> componentsByFacet;

        RenderedCard(JPanel panel, List<Binding> bindings,
                     Map<String, JComponent> componentsByKey,
                     Map<String, JPanel> rowsByKey,
                     Map<Facet, List<JComponent>> componentsByFacet) {
            this.panel = panel;
            this.bindings = bindings;
            this.componentsByKey = componentsByKey;
            this.rowsByKey = rowsByKey;
            this.componentsByFacet = componentsByFacet;
        }

        public JPanel getPanel() { return panel; }

        public void load(Map<String, String> cfg) {
            for (Binding b : bindings) b.load(cfg);
        }

        public void save(Map<String, String> out) {
            for (Binding b : bindings) b.save(out);
        }

        /** Liefert das gerenderte Eingabefeld zum Persistenz-Key (oder {@code null}). */
        public JComponent getComponent(String key) { return componentsByKey.get(key); }

        /**
         * Liefert den Row-Container ({@link BorderLayout}) eines Feldes, sodass Aufrufer
         * weitere Komponenten rechts ({@link BorderLayout#EAST}) anhängen können —
         * z.&nbsp;B. einen 🔄-Fetch- oder Browse-Button. Liefert {@code null}, falls der
         * Key keinen Row-Container besitzt (z.&nbsp;B. CHECKBOX/INFO/HEADER_TABLE).
         */
        public JPanel getRow(String key) { return rowsByKey.get(key); }

        /** Alle Komponenten (Label + Feld), die zu einer Facet gehören — für Grayout-Listen. */
        public List<JComponent> getFacetComponents(Facet f) {
            List<JComponent> l = componentsByFacet.get(f);
            return l != null ? l : java.util.Collections.<JComponent>emptyList();
        }
    }

    /** Eine Binding kennt sowohl ihren Lade- als auch ihren Speicher-Schritt. */
    private interface Binding {
        void load(Map<String, String> cfg);
        void save(Map<String, String> out);
    }

    public static RenderedCard render(ProviderDef def, Set<Facet> facets) {
        return render(def, facets, null);
    }

    /**
     * Wie {@link #render(ProviderDef, Set)}, akzeptiert aber zusätzlich einen
     * {@code useProxySupplier}, dessen Wert beim Klick auf 🔄 (Modelle laden) und
     * 🧪 (Verbindung testen) ausgewertet wird. Ist der Supplier {@code null} oder
     * liefert er {@code true}, wird wie bisher die globale Proxy-Konfiguration
     * verwendet; liefert er {@code false}, wird DIRECT (kein Proxy) erzwungen.
     * Wird typischerweise von einer per-Tab-Checkbox gespeist.
     */
    public static RenderedCard render(ProviderDef def, Set<Facet> facets,
                                      BooleanSupplier useProxySupplier) {
        List<Binding> bindings = new ArrayList<Binding>();
        Map<String, JComponent> byKey = new LinkedHashMap<String, JComponent>();
        Map<String, JPanel> rowsByKey = new LinkedHashMap<String, JPanel>();
        Map<Facet, List<JComponent>> byFacet = new EnumMap<Facet, List<JComponent>>(Facet.class);

        if (def.subModes.isEmpty()) {
            // Einfacher Provider — items in Builder-Reihenfolge rendern.
            FormBuilder fb = new FormBuilder();
            renderItems(fb, def.items, facets, bindings, byKey, rowsByKey, byFacet, useProxySupplier);
            return new RenderedCard(pinToTop(fb.panel), bindings, byKey, rowsByKey, byFacet);
        }

        // Sub-Modus-Provider (PRIVATE_CLOUD).
        return renderWithSubModes(def, facets, bindings, byKey, rowsByKey, byFacet, useProxySupplier);
    }

    // -----------------------------------------------------------------
    //  Sub-Modus-Rendering (eine Modus-Combo in jedem Sub-Card, geteiltes Model)
    // -----------------------------------------------------------------
    private static RenderedCard renderWithSubModes(ProviderDef def, Set<Facet> facets,
                                                   final List<Binding> bindings,
                                                   Map<String, JComponent> byKey,
                                                   Map<String, JPanel> rowsByKey,
                                                   Map<Facet, List<JComponent>> byFacet,
                                                   BooleanSupplier useProxySupplier) {
        final String[] storedValues = new String[def.subModes.size()];
        final String[] displayLabels = new String[def.subModes.size()];
        for (int i = 0; i < def.subModes.size(); i++) {
            displayLabels[i] = def.subModes.get(i).displayLabel;
            storedValues[i] = def.subModes.get(i).storedValue;
        }
        final DefaultComboBoxModel<String> modeModel =
                new DefaultComboBoxModel<String>(displayLabels);

        final CardLayout subCardLayout = new CardLayout();
        final JPanel subCards = new JPanel(subCardLayout);

        List<JComboBox<String>> modeCombos = new ArrayList<JComboBox<String>>();
        for (ProviderDef.SubMode mode : def.subModes) {
            FormBuilder fb = new FormBuilder();
            JComboBox<String> modeCombo = new JComboBox<String>(modeModel);
            modeCombos.add(modeCombo);
            fb.addRow(def.subModeLabel != null ? def.subModeLabel : "Modus:", modeCombo);

            renderItems(fb, mode.items, facets, bindings, byKey, rowsByKey, byFacet, useProxySupplier);

            subCards.add(pinToTop(fb.panel), mode.displayLabel);
        }

        ActionListener modeSwitch = new ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                Object sel = modeModel.getSelectedItem();
                if (sel != null) subCardLayout.show(subCards, sel.toString());
            }
        };
        for (JComboBox<String> c : modeCombos) c.addActionListener(modeSwitch);
        subCardLayout.show(subCards, def.subModes.get(0).displayLabel);

        // Modus-Persistenz als eigene Binding.
        final String key = def.subModeKey;
        bindings.add(new Binding() {
            public void load(Map<String, String> cfg) {
                String stored = cfg.getOrDefault(key, storedValues[0]);
                for (int i = 0; i < storedValues.length; i++) {
                    if (storedValues[i].equalsIgnoreCase(stored)) {
                        modeModel.setSelectedItem(displayLabels[i]);
                        subCardLayout.show(subCards, displayLabels[i]);
                        return;
                    }
                }
                modeModel.setSelectedItem(displayLabels[0]);
                subCardLayout.show(subCards, displayLabels[0]);
            }
            public void save(Map<String, String> out) {
                Object sel = modeModel.getSelectedItem();
                String selLabel = sel != null ? sel.toString() : displayLabels[0];
                for (int i = 0; i < displayLabels.length; i++) {
                    if (displayLabels[i].equals(selLabel)) {
                        out.put(key, storedValues[i]);
                        return;
                    }
                }
                out.put(key, storedValues[0]);
            }
        });

        JPanel outer = new JPanel(new BorderLayout());
        outer.add(subCards, BorderLayout.CENTER);
        return new RenderedCard(outer, bindings, byKey, rowsByKey, byFacet);
    }

    // -----------------------------------------------------------------
    //  Rendering der Items (FieldSpec | ModelSlot in Originalreihenfolge)
    // -----------------------------------------------------------------
    private static void renderItems(FormBuilder fb, List<ProviderDef.Item> items,
                                    Set<Facet> facets, List<Binding> bindings,
                                    Map<String, JComponent> byKey,
                                    Map<String, JPanel> rowsByKey,
                                    Map<Facet, List<JComponent>> byFacet,
                                    BooleanSupplier useProxySupplier) {
        for (ProviderDef.Item it : items) {
            if (it instanceof FieldSpec) {
                FieldSpec spec = (FieldSpec) it;
                if (spec.requiredFacet != null && !facets.contains(spec.requiredFacet)) continue;
                if (spec.section != null && !spec.section.isEmpty()) fb.addSection(spec.section);
                renderField(fb, spec, bindings, byKey, rowsByKey, byFacet);
            } else if (it instanceof ModelSlot) {
                ModelSlot slot = (ModelSlot) it;
                if (!facets.contains(slot.facet)) continue;
                renderSlot(fb, slot, bindings, byKey, rowsByKey, byFacet, useProxySupplier);
            }
        }
    }

    private static void renderField(FormBuilder fb, FieldSpec spec, List<Binding> bindings,
                                    Map<String, JComponent> byKey,
                                    Map<String, JPanel> rowsByKey,
                                    Map<Facet, List<JComponent>> byFacet) {
        switch (spec.type) {
            case TEXT: {
                final JTextField tf = new JTextField(spec.defaultValue != null ? spec.defaultValue : "", 30);
                if (spec.tooltip != null) tf.setToolTipText(spec.tooltip);
                JLabel lbl = new JLabel(spec.label);
                JPanel wrap;
                if (spec.withResetButton) {
                    final String defVal = spec.defaultValue != null ? spec.defaultValue : "";
                    JButton reset = squareResetButton(() -> tf.setText(defVal));
                    wrap = fb.addRowGetWrapper(lbl, tf, reset);
                    trackFacet(byFacet, spec.requiredFacet, lbl, tf, reset);
                } else {
                    wrap = fb.addRowGetWrapper(lbl, tf, null);
                    trackFacet(byFacet, spec.requiredFacet, lbl, tf);
                }
                byKey.put(spec.key, tf);
                rowsByKey.put(spec.key, wrap);
                bindings.add(textBinding(spec.key, tf, spec.defaultValue));
                break;
            }
            case PASSWORD: {
                JPasswordField pf = new JPasswordField(spec.defaultValue != null ? spec.defaultValue : "", 30);
                if (spec.tooltip != null) pf.setToolTipText(spec.tooltip);
                JLabel lbl = new JLabel(spec.label);
                JPanel wrap = fb.addRowGetWrapper(lbl, pf, null);
                byKey.put(spec.key, pf);
                rowsByKey.put(spec.key, wrap);
                trackFacet(byFacet, spec.requiredFacet, lbl, pf);
                bindings.add(passwordBinding(spec.key, pf, spec.defaultValue));
                break;
            }
            case COMBO_EDITABLE: {
                JComboBox<String> combo = newEditableCombo(spec.defaultValue);
                if (spec.tooltip != null) combo.setToolTipText(spec.tooltip);
                JLabel lbl = new JLabel(spec.label);
                JPanel wrap = fb.addRowGetWrapper(lbl, combo, null);
                byKey.put(spec.key, combo);
                rowsByKey.put(spec.key, wrap);
                trackFacet(byFacet, spec.requiredFacet, lbl, combo);
                bindings.add(comboBinding(spec.key, combo, spec.defaultValue));
                break;
            }
            case COMBO_FIXED: {
                JComboBox<String> combo = new JComboBox<String>(spec.choices);
                if (spec.defaultValue != null) combo.setSelectedItem(spec.defaultValue);
                if (spec.tooltip != null) combo.setToolTipText(spec.tooltip);
                JLabel lbl = new JLabel(spec.label);
                JPanel wrap = fb.addRowGetWrapper(lbl, combo, null);
                byKey.put(spec.key, combo);
                rowsByKey.put(spec.key, wrap);
                trackFacet(byFacet, spec.requiredFacet, lbl, combo);
                bindings.add(fixedComboBinding(spec.key, combo, spec.defaultValue));
                break;
            }
            case INT_SPINNER: {
                int def = parseIntSafe(spec.defaultValue, spec.spinnerMin);
                JSpinner sp = new JSpinner(new SpinnerNumberModel(def, spec.spinnerMin, spec.spinnerMax, spec.spinnerStep));
                if (spec.tooltip != null) sp.setToolTipText(spec.tooltip);
                JLabel lbl = new JLabel(spec.label);
                JPanel wrap = fb.addRowGetWrapper(lbl, sp, null);
                byKey.put(spec.key, sp);
                rowsByKey.put(spec.key, wrap);
                trackFacet(byFacet, spec.requiredFacet, lbl, sp);
                bindings.add(spinnerBinding(spec.key, sp, def));
                break;
            }
            case CHECKBOX: {
                JCheckBox cb = new JCheckBox(spec.label);
                cb.setSelected(Boolean.parseBoolean(spec.defaultValue));
                if (spec.tooltip != null) cb.setToolTipText(spec.tooltip);
                fb.addRow("", cb);
                byKey.put(spec.key, cb);
                trackFacet(byFacet, spec.requiredFacet, cb);
                bindings.add(checkboxBinding(spec.key, cb, Boolean.parseBoolean(spec.defaultValue)));
                break;
            }
            case INFO: {
                JLabel info = makeInfoLabel(spec.label);
                fb.addWide(info);
                trackFacet(byFacet, spec.requiredFacet, info);
                // Kein Binding — reines Anzeige-Element.
                break;
            }
            case HEADER_TABLE:
                renderHeaderTable(fb, spec, bindings, byFacet);
                break;
            default:
                throw new IllegalStateException("Unbekannter FieldSpec-Type: " + spec.type);
        }
    }

    private static void trackFacet(Map<Facet, List<JComponent>> byFacet, Facet f, JComponent... comps) {
        if (f == null) return;
        List<JComponent> list = byFacet.get(f);
        if (list == null) {
            list = new ArrayList<JComponent>();
            byFacet.put(f, list);
        }
        for (JComponent c : comps) list.add(c);
    }

    private static void renderHeaderTable(FormBuilder fb, final FieldSpec spec,
                                          List<Binding> bindings,
                                          Map<Facet, List<JComponent>> byFacet) {
        final DefaultTableModel model = new DefaultTableModel(new Object[]{"Header", "Wert"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return true; }
        };
        final JTable table = new JTable(model);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(500, 120));
        fb.addWide(scroll);

        JButton add = new JButton("➕ Header");
        add.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            model.addRow(new Object[]{"", ""});
            int last = model.getRowCount() - 1;
            table.setRowSelectionInterval(last, last);
            table.editCellAt(last, 0);
            Component editor = table.getEditorComponent();
            if (editor != null) editor.requestFocusInWindow();
        });
        JButton remove = new JButton("➖ Header");
        remove.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            int[] rows = table.getSelectedRows();
            for (int i = rows.length - 1; i >= 0; i--) model.removeRow(rows[i]);
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.add(add);
        btns.add(remove);
        if (spec.withResetButton) {
            JButton reset = new JButton("↺ Defaults");
            reset.setToolTipText("Header auf Standard zurücksetzen");
            reset.addActionListener(e -> {
                if (table.isEditing()) table.getCellEditor().stopCellEditing();
                model.setRowCount(0);
                for (Map.Entry<String, String> en : spec.headerDefaults.entrySet()) {
                    model.addRow(new Object[]{en.getKey(), en.getValue()});
                }
            });
            btns.add(reset);
        }
        fb.addWide(btns);

        final String prefix = spec.key; // bereits inklusive "."
        bindings.add(new Binding() {
            public void load(Map<String, String> cfg) {
                model.setRowCount(0);
                for (Map.Entry<String, String> e : cfg.entrySet()) {
                    String k = e.getKey();
                    if (k != null && k.startsWith(prefix)) {
                        String name = k.substring(prefix.length());
                        if (!name.isEmpty()) {
                            model.addRow(new Object[]{name, e.getValue() == null ? "" : e.getValue()});
                        }
                    }
                }
                if (model.getRowCount() == 0) {
                    for (Map.Entry<String, String> en : spec.headerDefaults.entrySet()) {
                        model.addRow(new Object[]{en.getKey(), en.getValue()});
                    }
                }
            }
            public void save(Map<String, String> out) {
                if (table.isEditing()) table.getCellEditor().stopCellEditing();
                for (int i = 0; i < model.getRowCount(); i++) {
                    String name = Objects.toString(model.getValueAt(i, 0), "").trim();
                    String value = Objects.toString(model.getValueAt(i, 1), "");
                    if (!name.isEmpty()) out.put(prefix + name, value);
                }
            }
        });
        trackFacet(byFacet, spec.requiredFacet, scroll, btns);
    }

    // -----------------------------------------------------------------
    //  Rendering eines einzelnen Modell-Slots
    // -----------------------------------------------------------------
    private static void renderSlot(FormBuilder fb, ModelSlot slot, List<Binding> bindings,
                                   Map<String, JComponent> byKey,
                                   Map<String, JPanel> rowsByKey,
                                   Map<Facet, List<JComponent>> byFacet,
                                   final BooleanSupplier useProxySupplier) {
        // sectionLabel == "" unterdrückt den Section-Header (z. B. wenn der Slot in einer
        // bereits geöffneten Sektion bleibt).
        String section = slot.sectionLabel != null ? slot.sectionLabel : defaultSectionFor(slot.facet);
        if (section != null && !section.isEmpty()) fb.addSection(section);

        if (slot.urlKey != null) {
            final JTextField urlField = new JTextField(slot.urlDefault != null ? slot.urlDefault : "", 30);
            if (slot.urlTooltip != null) urlField.setToolTipText(slot.urlTooltip);
            JLabel urlLbl = new JLabel(slot.urlLabel != null ? slot.urlLabel : "URL:");
            JPanel wrap = fb.addRowGetWrapper(urlLbl, urlField, null);
            byKey.put(slot.urlKey, urlField);
            rowsByKey.put(slot.urlKey, wrap);
            trackFacet(byFacet, slot.facet, urlLbl, urlField);
            bindings.add(textBinding(slot.urlKey, urlField, slot.urlDefault));
        }
        if (slot.endpointKey != null) {
            final JTextField epField = new JTextField(slot.endpointDefault != null ? slot.endpointDefault : "", 30);
            JLabel epLbl = new JLabel(slot.endpointLabel != null ? slot.endpointLabel : "Endpoint:");
            final String epDef = slot.endpointDefault != null ? slot.endpointDefault : "";
            JButton reset = squareResetButton(() -> epField.setText(epDef));
            JPanel wrap = fb.addRowGetWrapper(epLbl, epField, reset);
            byKey.put(slot.endpointKey, epField);
            rowsByKey.put(slot.endpointKey, wrap);
            trackFacet(byFacet, slot.facet, epLbl, epField, reset);
            bindings.add(textBinding(slot.endpointKey, epField, slot.endpointDefault));
        }
        // Modellfeld (Combo oder Textfield).
        JLabel modelLbl = new JLabel(slot.modelLabel);
        if (slot.modelType == ModelSlot.ModelType.TEXT) {
            JTextField mf = new JTextField(slot.modelDefault != null ? slot.modelDefault : "", 30);
            if (slot.modelTooltip != null) mf.setToolTipText(slot.modelTooltip);
            JPanel wrap;
            if (slot.connectionTester != null) {
                final JLabel statusLabel = new JLabel(" ");
                statusLabel.setFont(statusLabel.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
                final JButton test = new JButton("🧪");
                test.setToolTipText("Verbindung mit diesem Endpunkt testen");
                test.setMargin(new Insets(2, 4, 2, 4));
                final List<Binding> bindingsRef = bindings;
                final ModelSlot slotRef = slot;
                test.addActionListener(new java.awt.event.ActionListener() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        Map<String, String> snapshot = new java.util.HashMap<String, String>();
                        for (Binding b : bindingsRef) {
                            try { b.save(snapshot); } catch (Exception ex) { /* ignore */ }
                        }
                        ConnectionTestPlan plan;
                        try { plan = slotRef.connectionTester.apply(snapshot); }
                        catch (Exception ex) {
                            statusLabel.setText("❌ " + ex.getMessage());
                            statusLabel.setForeground(Color.RED);
                            return;
                        }
                        boolean up = useProxySupplier == null || useProxySupplier.getAsBoolean();
                        ConnectionTester.testAsync(statusLabel, test, plan, up);
                    }
                });
                wrap = fb.addRowGetWrapper(modelLbl, mf, test);
                fb.addWide(statusLabel);
                trackFacet(byFacet, slot.facet, modelLbl, mf, test, statusLabel);
            } else {
                wrap = fb.addRowGetWrapper(modelLbl, mf, null);
                trackFacet(byFacet, slot.facet, modelLbl, mf);
            }
            byKey.put(slot.modelKey, mf);
            rowsByKey.put(slot.modelKey, wrap);
            bindings.add(textBinding(slot.modelKey, mf, slot.modelDefault));
        } else {
            final JComboBox<String> combo = newEditableCombo(slot.modelDefault);
            if (slot.modelTooltip != null) combo.setToolTipText(slot.modelTooltip);
            JPanel wrap;

            // Status-Label wird ggf. von Fetch- UND Test-Button genutzt.
            final boolean hasFetcher = slot.modelsFetcher != null;
            final boolean hasTester = slot.connectionTester != null;
            final boolean hasLegacyFetchHook = slot.withModelFetchButton && !hasFetcher;

            final JLabel statusLabel;
            if (hasFetcher || hasTester) {
                statusLabel = new JLabel(" ");
                statusLabel.setFont(statusLabel.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
            } else {
                statusLabel = null;
            }

            // Trail-Container: 🔄 (fetch) + 🧪 (test) nebeneinander.
            JPanel trail = null;
            JButton fetch = null;
            JButton test = null;
            if (hasFetcher || hasTester || hasLegacyFetchHook) {
                trail = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                trail.setOpaque(false);
                if (hasFetcher) {
                    fetch = new JButton("🔄");
                    fetch.setToolTipText("Verfügbare Modelle abrufen");
                    fetch.setMargin(new Insets(2, 4, 2, 4));
                    final List<Binding> bindingsRef = bindings;
                    final ModelSlot slotRef = slot;
                    final JLabel statusRef = statusLabel;
                    final JComboBox<String> comboRef = combo;
                    fetch.addActionListener(new java.awt.event.ActionListener() {
                        @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                            Map<String, String> snapshot = new java.util.HashMap<String, String>();
                            for (Binding b : bindingsRef) {
                                try { b.save(snapshot); } catch (Exception ex) { /* ignore */ }
                            }
                            ModelsFetchPlan plan;
                            try {
                                plan = slotRef.modelsFetcher.apply(snapshot);
                            } catch (Exception ex) {
                                statusRef.setText("❌ " + ex.getMessage());
                                statusRef.setForeground(Color.RED);
                                return;
                            }
                            if (plan == null || plan.url == null || plan.url.isEmpty()) {
                                String hint = (plan != null && plan.errorHint != null)
                                        ? plan.errorHint : "⚠️ Endpunkt nicht konfiguriert";
                                statusRef.setText(hint);
                                statusRef.setForeground(new Color(180, 100, 0));
                                return;
                            }
                            ModelsFetcher.fetchAsync(comboRef, statusRef, plan.url, plan.headers);
                        }
                    });
                    trail.add(fetch);
                } else if (hasLegacyFetchHook) {
                    fetch = new JButton("🔄");
                    fetch.setToolTipText("(coming soon)");
                    fetch.setEnabled(false);
                    trail.add(fetch);
                }
                if (hasTester) {
                    test = new JButton("🧪");
                    test.setToolTipText("Verbindung mit diesem Endpunkt testen");
                    test.setMargin(new Insets(2, 4, 2, 4));
                    final List<Binding> bindingsRef = bindings;
                    final ModelSlot slotRef = slot;
                    final JLabel statusRef = statusLabel;
                    final JButton testRef = test;
                    test.addActionListener(new java.awt.event.ActionListener() {
                        @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                            Map<String, String> snapshot = new java.util.HashMap<String, String>();
                            for (Binding b : bindingsRef) {
                                try { b.save(snapshot); } catch (Exception ex) { /* ignore */ }
                            }
                            ConnectionTestPlan plan;
                            try {
                                plan = slotRef.connectionTester.apply(snapshot);
                            } catch (Exception ex) {
                                statusRef.setText("❌ " + ex.getMessage());
                                statusRef.setForeground(Color.RED);
                                return;
                            }
                            boolean up = useProxySupplier == null || useProxySupplier.getAsBoolean();
                            ConnectionTester.testAsync(statusRef, testRef, plan, up);
                        }
                    });
                    trail.add(test);
                }
            }

            if (trail != null) {
                wrap = fb.addRowGetWrapper(modelLbl, combo, trail);
                if (statusLabel != null) fb.addWide(statusLabel);
                List<JComponent> tracked = new ArrayList<JComponent>();
                tracked.add(modelLbl);
                tracked.add(combo);
                if (fetch != null) tracked.add(fetch);
                if (test != null) tracked.add(test);
                if (statusLabel != null) tracked.add(statusLabel);
                trackFacet(byFacet, slot.facet, tracked.toArray(new JComponent[0]));
            } else {
                wrap = fb.addRowGetWrapper(modelLbl, combo, null);
                trackFacet(byFacet, slot.facet, modelLbl, combo);
            }
            byKey.put(slot.modelKey, combo);
            rowsByKey.put(slot.modelKey, wrap);
            bindings.add(comboBinding(slot.modelKey, combo, slot.modelDefault));
        }
    }

    private static String defaultSectionFor(Facet f) {
        switch (f) {
            case CHAT: return "Chat";
            case EMBEDDINGS: return "Embeddings";
            case RERANKER: return "Reranker";
            case AUDIO: return "Audio";
            case RESPONSES: return "Responses-API";
            default: return f.name();
        }
    }

    // -----------------------------------------------------------------
    //  Binding-Factories
    // -----------------------------------------------------------------
    private static Binding textBinding(final String key, final JTextField tf, final String def) {
        return new Binding() {
            public void load(Map<String, String> cfg) { tf.setText(cfg.getOrDefault(key, def != null ? def : "")); }
            public void save(Map<String, String> out) { out.put(key, tf.getText().trim()); }
        };
    }

    private static Binding passwordBinding(final String key, final JPasswordField pf, final String def) {
        return new Binding() {
            public void load(Map<String, String> cfg) { pf.setText(cfg.getOrDefault(key, def != null ? def : "")); }
            public void save(Map<String, String> out) { out.put(key, new String(pf.getPassword()).trim()); }
        };
    }

    private static Binding comboBinding(final String key, final JComboBox<String> combo, final String def) {
        return new Binding() {
            public void load(Map<String, String> cfg) {
                combo.setSelectedItem(cfg.getOrDefault(key, def != null ? def : ""));
            }
            public void save(Map<String, String> out) {
                Object sel = combo.getEditor() != null ? combo.getEditor().getItem() : combo.getSelectedItem();
                out.put(key, Objects.toString(sel, "").trim());
            }
        };
    }

    private static Binding fixedComboBinding(final String key, final JComboBox<String> combo, final String def) {
        return new Binding() {
            public void load(Map<String, String> cfg) {
                combo.setSelectedItem(cfg.getOrDefault(key, def != null ? def : ""));
            }
            public void save(Map<String, String> out) {
                out.put(key, Objects.toString(combo.getSelectedItem(), def != null ? def : ""));
            }
        };
    }

    private static Binding spinnerBinding(final String key, final JSpinner sp, final int def) {
        return new Binding() {
            public void load(Map<String, String> cfg) {
                try { sp.setValue(Integer.parseInt(cfg.getOrDefault(key, String.valueOf(def)))); }
                catch (NumberFormatException e) { sp.setValue(def); }
            }
            public void save(Map<String, String> out) { out.put(key, sp.getValue().toString()); }
        };
    }

    private static Binding checkboxBinding(final String key, final JCheckBox cb, final boolean def) {
        return new Binding() {
            public void load(Map<String, String> cfg) {
                cb.setSelected(Boolean.parseBoolean(cfg.getOrDefault(key, String.valueOf(def))));
            }
            public void save(Map<String, String> out) { out.put(key, String.valueOf(cb.isSelected())); }
        };
    }

    // -----------------------------------------------------------------
    //  Hilfen
    // -----------------------------------------------------------------
    private static JComboBox<String> newEditableCombo(String defaultValue) {
        JComboBox<String> c = new JComboBox<String>();
        c.setEditable(true);
        if (defaultValue != null && !defaultValue.isEmpty()) c.setSelectedItem(defaultValue);
        return c;
    }

    private static int parseIntSafe(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    /** Erzeugt ein graues, kursives Info-Label (HTML erlaubt). */
    private static JLabel makeInfoLabel(String html) {
        JLabel l = new JLabel("<html><i>ℹ\u00a0" + html + "</i></html>");
        l.setForeground(new Color(120, 120, 120));
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        return l;
    }

    /** Kleiner quadratischer ↺-Reset-Button (identisch zu AiSettingsPanel.squareResetButton). */
    private static JButton squareResetButton(Runnable onClick) {
        JButton b = new JButton("↺");
        b.setToolTipText("Auf Default zurücksetzen");
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        Dimension d = new Dimension(22, 22);
        b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
        b.setFocusable(false);
        b.addActionListener(e -> onClick.run());
        return b;
    }

    /**
     * Heftet ein {@link GridBagLayout}-Panel oben an: ohne diesen Wrapper würde
     * {@code GridBagLayout} seinen Inhalt vertikal zentrieren, sobald der umgebende
     * Container höher ist als die Summe der Zeilen-Preferred-Heights — was im
     * Embeddings-/Reranker-Tab als unschöne Lücken oberhalb und unterhalb der
     * Provider-Felder sichtbar wird. {@code BorderLayout.NORTH} fixiert das Grid
     * auf seine Preferred-Height; der Rest des Containers bleibt unten leer.
     */
    private static JPanel pinToTop(JPanel content) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(content, BorderLayout.NORTH);
        return wrap;
    }

    /** Schlanker GridBag-Builder mit Section-/Row-/Wide-Helfern. */
    static final class FormBuilder {
        final JPanel panel = new JPanel(new GridBagLayout());
        int row = 0;

        void addRow(String label, JComponent field) {
            addRow(new JLabel(label), field);
        }

        void addRow(JLabel label, JComponent field) {
            addRowGetWrapper(label, field, null);
        }

        /** Variante: Feld + kleiner Trail-Button rechts. */
        void addRowWithButton(JLabel label, JComponent field, JComponent button) {
            addRowGetWrapper(label, field, button);
        }

        /**
         * Fügt eine Zeile hinzu und liefert den BorderLayout-Wrapper zurück, an den
         * Aufrufer später weitere Komponenten ({@link BorderLayout#EAST}) anhängen können.
         */
        JPanel addRowGetWrapper(JLabel label, JComponent field, JComponent eastButton) {
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(4, 4, 4, 4);
            g.anchor = GridBagConstraints.WEST;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.gridx = 0; g.gridy = row; g.weightx = 0;
            panel.add(label, g);
            JPanel inner = new JPanel(new BorderLayout(4, 0));
            inner.add(field, BorderLayout.CENTER);
            if (eastButton != null) inner.add(eastButton, BorderLayout.EAST);
            g.gridx = 1; g.weightx = 1;
            panel.add(inner, g);
            row++;
            return inner;
        }

        void addSection(String title) {
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(10, 4, 4, 4);
            g.anchor = GridBagConstraints.WEST;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.gridx = 0; g.gridy = row; g.gridwidth = 2; g.weightx = 1;
            JLabel lbl = new JLabel("── " + title + " ──");
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            panel.add(lbl, g);
            row++;
        }

        void addWide(JComponent comp) {
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(4, 4, 4, 4);
            g.anchor = GridBagConstraints.WEST;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.gridx = 0; g.gridy = row; g.gridwidth = 2; g.weightx = 1;
            panel.add(comp, g);
            row++;
        }
    }
}

