package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.summarizer.SummarizerSettings;
import de.bund.zrb.ui.settings.provider.Facet;
import de.bund.zrb.ui.settings.provider.ProviderConfigPanel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings panel for the optional auxiliary Summarizer service.
 *
 * <p>Folgt exakt der Struktur von {@link RerankerSettingsPanel}:
 * der generische {@link ProviderConfigPanel} (gefiltert auf {@link Facet#SUMMARIZE})
 * rendert pro AI-Provider exakt jene Felder, die für Kurz-Zusammenfassungen
 * nötig sind. Die summarizer-spezifischen Parameter (max. Tokens, Cache,
 * System-Prompt, UML-Schalter, Timeout) liegen darunter in eigenen Sektionen.</p>
 *
 * <p>Wie beim Reranker-Override gibt es einen {@code overwrite}-Schalter: ist er
 * <em>aus</em>, kommen Provider/URL/Modell/API-Key aus dem AI-Tab "Allgemein";
 * ist er <em>an</em>, werden die Werte aus diesem Tab verwendet (siehe
 * {@link SummarizerSettings#fromConfig(Map, Map)}).</p>
 */
public class SummarizerSettingsPanel extends AbstractSettingsPanel {

    private final ProviderConfigPanel providerPanel;
    private final JCheckBox enabledBox;
    private final JCheckBox overwriteBox;
    private final JLabel overwriteInfoLabel;
    private final JSpinner maxTokensSpinner;
    private final JSpinner timeoutSpinner;
    private final JCheckBox cacheEnabledBox;
    private final JSpinner cacheSizeSpinner;
    private final JCheckBox umlEnabledBox;
    private final JTextArea systemPromptArea;
    private final JButton resetPromptButton;
    private final JCheckBox useProxyBox;
    private final JCheckBox useProxyAuthBox;
    private final JCheckBox useE2eBox;

    public SummarizerSettingsPanel() {
        super("summarizer", "Summarizer");
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Vorab anlegen, damit der Supplier sie referenzieren kann.
        useProxyBox = new JCheckBox("Proxy verwenden");
        useProxyBox.setToolTipText(
                "Wenn aktiv, werden Modell-Abruf und Verbindungstest über den global "
                        + "konfigurierten Proxy geleitet. Aus = direkt (DIRECT).");
        useProxyAuthBox = new JCheckBox("Proxy-Auth");
        useProxyAuthBox.setToolTipText(
                "Wenn aktiv, werden Anfragen mit Basic-Auth Credentials aus dem Proxy-Tab versehen.");
        useE2eBox = new JCheckBox("E2E");
        useE2eBox.setToolTipText(
                "Wenn aktiv, werden Anfragen mit AES-256-GCM verschlüsselt (Passwort im Proxy-Tab).");

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // ── Header ────────────────────────────────────────────────
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Summarizer (Auxiliary Mini-LLM)");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        JLabel subtitle = new JLabel(
                "Kleines, lokales Modell für Kurz-Zusammenfassungen (UML-Blöcke, Datei-Tooltips, Auto-Titel).");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
        subtitle.setForeground(new Color(96, 96, 96));
        headerPanel.add(subtitle, BorderLayout.SOUTH);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(headerPanel);

        // ── Status ────────────────────────────────────────────────
        JPanel enablePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enablePanel.setBorder(new TitledBorder("Summarizer-Status"));
        enabledBox = new JCheckBox("Dedizierten Summarizer aktivieren");
        enabledBox.setToolTipText(
                "Aus = Anfragen werden an den unter \"Allgemein\" gewählten Chat-Provider delegiert.\n"
                        + "An  = Eigener Summarizer-Provider/Modell wird verwendet.");
        enablePanel.add(enabledBox);
        umlEnabledBox = new JCheckBox("AI-Zusammenfassung in UML-Diagrammen aktivieren");
        umlEnabledBox.setToolTipText(
                "Wenn aktiv, ersetzt der Summarizer in der Mermaid/UML-Ansicht die heuristischen "
                        + "„X Anweisungen\"-Knoten durch eine inhaltliche Kurzbeschreibung.");
        enablePanel.add(umlEnabledBox);
        enablePanel.add(useProxyBox);
        enablePanel.add(useProxyAuthBox);
        enablePanel.add(useE2eBox);
        mainPanel.add(enablePanel);

        // ── Provider-Override (generischer Provider Selector) ────
        JPanel providerSection = new JPanel(new BorderLayout(0, 6));
        providerSection.setBorder(new TitledBorder("Summarizer-Provider (Override)"));
        overwriteBox = new JCheckBox(
                "Eigene Summarizer-Konfiguration verwenden (Override)");
        overwriteBox.setToolTipText(
                "Wenn deaktiviert, werden die Summarizer-spezifischen Felder aus dem Tab \"Allgemein\" "
                        + "des dort gewählten Providers verwendet. Wenn aktiviert, kann hier ein anderer "
                        + "Provider und für diesen Provider alle für den Summarizer nötigen Felder "
                        + "(URL, Modell, API-Key, Endpoint-Pfad) abweichend gesetzt werden.");
        providerSection.add(overwriteBox, BorderLayout.NORTH);

        overwriteInfoLabel = new JLabel();
        overwriteInfoLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        overwriteInfoLabel.setForeground(new Color(96, 96, 96));
        overwriteInfoLabel.setFont(overwriteInfoLabel.getFont().deriveFont(Font.PLAIN, 11f));

        providerPanel = new ProviderConfigPanel(EnumSet.of(Facet.SUMMARIZE),
                () -> useProxyBox.isSelected());
        JPanel providerInner = new JPanel(new BorderLayout(0, 4));
        providerInner.add(overwriteInfoLabel, BorderLayout.NORTH);
        providerInner.add(providerPanel, BorderLayout.CENTER);
        providerSection.add(providerInner, BorderLayout.CENTER);
        mainPanel.add(providerSection);

        // ── Output-Parameter ──────────────────────────────────────
        JPanel paramPanel = new JPanel(new GridBagLayout());
        paramPanel.setBorder(new TitledBorder("Antwort-Limits"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        paramPanel.add(new JLabel("Max. Tokens:"), gbc);
        gbc.gridx = 1;
        maxTokensSpinner = new JSpinner(new SpinnerNumberModel(64, 8, 512, 8));
        maxTokensSpinner.setToolTipText("Hartes Token-Limit pro Aufruf. Empfehlung: 32–128.");
        paramPanel.add(maxTokensSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        paramPanel.add(new JLabel("Timeout (Sekunden):"), gbc);
        gbc.gridx = 1;
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(15, 2, 120, 1));
        paramPanel.add(timeoutSpinner, gbc);

        mainPanel.add(paramPanel);

        // ── Cache ─────────────────────────────────────────────────
        JPanel cachePanel = new JPanel(new GridBagLayout());
        cachePanel.setBorder(new TitledBorder("Cache"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        cacheEnabledBox = new JCheckBox("Antworten cachen (LRU, pro Eingabe-Hash)");
        cacheEnabledBox.setToolTipText(
                "Identische Eingaben werden nur einmal an das Modell geschickt. "
                        + "Deaktivieren, falls Cache-Effekte beim Debugging stören.");
        cachePanel.add(cacheEnabledBox, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        cachePanel.add(new JLabel("Cache-Größe:"), gbc);
        gbc.gridx = 1;
        cacheSizeSpinner = new JSpinner(new SpinnerNumberModel(1000, 16, 100000, 16));
        cachePanel.add(cacheSizeSpinner, gbc);

        mainPanel.add(cachePanel);

        // ── System-Prompt ─────────────────────────────────────────
        JPanel promptPanel = new JPanel(new BorderLayout(4, 4));
        promptPanel.setBorder(new TitledBorder("System-Prompt"));
        systemPromptArea = new JTextArea(SummarizerSettings.DEFAULT_SYSTEM_PROMPT);
        systemPromptArea.setLineWrap(true);
        systemPromptArea.setWrapStyleWord(true);
        systemPromptArea.setRows(5);
        JScrollPane promptScroll = new JScrollPane(systemPromptArea);
        promptScroll.setPreferredSize(new Dimension(480, 110));
        promptPanel.add(promptScroll, BorderLayout.CENTER);

        JPanel promptActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resetPromptButton = new JButton("↺ Default wiederherstellen");
        resetPromptButton.addActionListener(e -> systemPromptArea.setText(SummarizerSettings.DEFAULT_SYSTEM_PROMPT));
        promptActions.add(resetPromptButton);
        JLabel promptHint = new JLabel(
                "Der Prompt wird vor jeden Aufruf gestellt. Bewusst kurz und imperativ halten.");
        promptHint.setFont(promptHint.getFont().deriveFont(Font.PLAIN, 11f));
        promptHint.setForeground(new Color(96, 96, 96));
        promptActions.add(promptHint);
        promptPanel.add(promptActions, BorderLayout.SOUTH);

        mainPanel.add(promptPanel);

        // ── Info ──────────────────────────────────────────────────
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(new TitledBorder("Hinweise"));
        JTextArea infoText = new JTextArea(
                "Der Summarizer ist ein kleines Hilfs-LLM für Kurz-Antworten:\n"
                        + "  • UML-Block-Labels (statt „5 Anweisungen\")\n"
                        + "  • Datei-Tooltips, Chat-Auto-Titel, JES-Job-Beschreibungen\n"
                        + "  • RAG-Chunk-Titel, Spalten-Beschreibungen\n\n"
                        + "Empfohlene Modelle (Default Ollama: " + SummarizerSettings.DEFAULT_MODEL + "):\n"
                        + "• qwen2.5:0.5b    (≈ 350 MB, sehr schnell auf CPU)\n"
                        + "• phi3:3.8b-mini  (höhere Qualität, langsamer)\n"
                        + "• tinyllama       (kleinstes Fallback)\n\n"
                        + "💡 Wenn der Summarizer aus ist, werden Aufrufe an den unter „Allgemein\"\n"
                        + "    konfigurierten Chat-Provider delegiert (mit denselben Limits).");
        infoText.setEditable(false);
        infoText.setBackground(getBackground());
        infoText.setFont(infoText.getFont().deriveFont(Font.PLAIN, 11f));
        infoPanel.add(infoText, BorderLayout.CENTER);
        mainPanel.add(infoPanel);

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        overwriteBox.addActionListener(e -> applyOverwriteState());
        cacheEnabledBox.addActionListener(e -> cacheSizeSpinner.setEnabled(cacheEnabledBox.isSelected()));
        loadFromSettings();
        applyOverwriteState();
        cacheSizeSpinner.setEnabled(cacheEnabledBox.isSelected());
    }

    private void applyOverwriteState() {
        boolean on = overwriteBox.isSelected();
        setEnabledRecursive(providerPanel, on);
        overwriteInfoLabel.setText(on
                ? "Override aktiv: Sämtliche summarizer-relevanten Felder stammen aus diesem Tab."
                : "Override inaktiv: Summarizer-Felder stammen aus den Summarizer-spezifischen Einstellungen unter \"Allgemein\".");
    }

    private static void setEnabledRecursive(Component c, boolean enabled) {
        c.setEnabled(enabled);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                setEnabledRecursive(child, enabled);
            }
        }
    }

    private void loadFromSettings() {
        Settings s = SettingsHelper.load();
        Map<String, String> cfg = s.summarizerConfig != null
                ? s.summarizerConfig : new HashMap<String, String>();

        enabledBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("enabled", "false")));
        overwriteBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("overwrite", "false")));
        umlEnabledBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("umlEnabled", "true")));
        cacheEnabledBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("cacheEnabled", "true")));
        useProxyBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("useProxy", "false")));
        useProxyAuthBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("useProxyAuth", "false")));
        useE2eBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("useE2e", "false")));

        try { maxTokensSpinner.setValue(Integer.parseInt(cfg.getOrDefault("maxTokens", "64"))); }
        catch (NumberFormatException e) { /* keep */ }
        try { timeoutSpinner.setValue(Integer.parseInt(cfg.getOrDefault("timeout", "15"))); }
        catch (NumberFormatException e) { /* keep */ }
        try { cacheSizeSpinner.setValue(Integer.parseInt(cfg.getOrDefault("cacheSize", "1000"))); }
        catch (NumberFormatException e) { /* keep */ }

        systemPromptArea.setText(cfg.getOrDefault("systemPrompt", SummarizerSettings.DEFAULT_SYSTEM_PROMPT));
        systemPromptArea.setCaretPosition(0);

        providerPanel.loadFromConfig(cfg);
    }

    @Override
    protected void applyToSettings(Settings s) {
        if (s.summarizerConfig == null) s.summarizerConfig = new HashMap<String, String>();
        // Strip stale custom-header keys so deleted Custom-mode headers actually disappear.
        s.summarizerConfig.keySet().removeIf(k -> k != null && k.startsWith("custom.header."));

        s.summarizerConfig.putAll(providerPanel.saveToConfig());
        s.summarizerConfig.put("enabled", String.valueOf(enabledBox.isSelected()));
        s.summarizerConfig.put("overwrite", String.valueOf(overwriteBox.isSelected()));
        s.summarizerConfig.put("umlEnabled", String.valueOf(umlEnabledBox.isSelected()));
        s.summarizerConfig.put("maxTokens", maxTokensSpinner.getValue().toString());
        s.summarizerConfig.put("timeout", timeoutSpinner.getValue().toString());
        s.summarizerConfig.put("cacheEnabled", String.valueOf(cacheEnabledBox.isSelected()));
        s.summarizerConfig.put("cacheSize", cacheSizeSpinner.getValue().toString());
        s.summarizerConfig.put("systemPrompt", systemPromptArea.getText());
        s.summarizerConfig.put("useProxy", String.valueOf(useProxyBox.isSelected()));
        s.summarizerConfig.put("useProxyAuth", String.valueOf(useProxyAuthBox.isSelected()));
        s.summarizerConfig.put("useE2e", String.valueOf(useE2eBox.isSelected()));
    }

    @Override
    protected void afterApply(Settings s) {
        // Cache leeren, falls Provider/Modell/Prompt geändert wurden — sonst liefert
        // der Cache veraltete Antworten für identische Eingabe-Hashes.
        try {
            de.bund.zrb.summarizer.SummarizerServiceImpl impl =
                    (de.bund.zrb.summarizer.SummarizerServiceImpl)
                            de.bund.zrb.summarizer.SummarizerServiceImpl.get();
            impl.clearCache();
        } catch (Exception ignored) {
            // Niemals Settings-Speichern wegen Cache-Reset abbrechen.
        }
    }

    /**
     * Exposes the "Summarizer aktiviert"-Checkbox so it can be reparented into a host
     * panel (the parent {@code AiSettingsPanel} tab bar).
     */
    public JCheckBox getEnabledBox() {
        return enabledBox;
    }

    /** Internal getter retained for compatibility with potential external test hooks. */
    @SuppressWarnings("unused")
    private JComponent getProviderPanel() { return providerPanel; }

    /** Test-Hook: synchroner Mini-Smoke-Test gegen den aktuell konfigurierten Service. */
    @SuppressWarnings("unused")
    private void smokeTest() {
        SwingWorker<String, Void> w = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return de.bund.zrb.summarizer.SummarizerServiceImpl.get()
                            .summarize("PERFORM A.\nPERFORM B.\nIF X > 0 THEN PERFORM C.",
                                    de.zrb.bund.api.SummarizeOptions.label(60).withFallback("3 Anweisungen"));
                } catch (Exception ex) {
                    return "❌ " + ex.getMessage();
                }
            }
        };
        w.execute();
    }
}

