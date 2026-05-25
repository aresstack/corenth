package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.rag.config.RerankerSettings;
import de.bund.zrb.rag.infrastructure.HttpRerankerClient;
import de.bund.zrb.rag.port.RerankerClient;
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
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings panel for the optional cross-encoder reranker stage.
 *
 * <p>Folgt exakt der Struktur von {@link de.bund.zrb.ui.settings.RagSettingsPanel}:
 * der generische {@link ProviderConfigPanel} (gefiltert auf {@link Facet#RERANKER})
 * rendert pro AI-Provider exakt jene Felder, die für Reranking nötig sind. Die
 * reranker-spezifischen Parameter (Top-N, Kandidaten-Pool, Score-Schwellwert, Timeout)
 * liegen darunter in einer eigenen Sektion.</p>
 *
 * <p>Wie beim Embeddings-Override gibt es einen {@code overwrite}-Schalter: ist er
 * <em>aus</em>, kommen Provider/URL/Modell/API-Key aus dem AI-Tab "Allgemein"; ist
 * er <em>an</em>, werden die Werte aus diesem Tab verwendet (siehe
 * {@link RerankerSettings#fromConfig(Map, Map)}).</p>
 */
public class RerankerSettingsPanel extends AbstractSettingsPanel {

    private final ProviderConfigPanel providerPanel;
    private final JCheckBox enabledBox;
    private final JCheckBox overwriteBox;
    private final JLabel overwriteInfoLabel;
    private final JSpinner topNSpinner;
    private final JSpinner candidatePoolSpinner;
    private final JTextField scoreThresholdField;
    private final JSpinner timeoutSpinner;
    private final JCheckBox useProxyBox;
    private final JCheckBox useProxyAuthBox;
    private final JCheckBox useE2eBox;
    private final JButton testButton;
    private final JLabel statusLabel;

    public RerankerSettingsPanel() {
        super("reranker", "Reranker");
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Vorab anlegen, damit der useProxy-Supplier sie an den ProviderConfigPanel
        // weiterreichen kann (Konstruktor-Reihenfolge: Checkbox vor Provider-Panel).
        useProxyBox = new JCheckBox("Proxy verwenden");
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
        JLabel titleLabel = new JLabel("Reranker-Konfiguration (Cross-Encoder)");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(headerPanel);

        // ── Status ────────────────────────────────────────────────
        JPanel enablePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enablePanel.setBorder(new TitledBorder("Reranker-Status"));
        enabledBox = new JCheckBox("Reranker aktiviert");
        enablePanel.add(enabledBox);
        mainPanel.add(enablePanel);

        // ── Provider-Override (generischer Provider Selector) ────
        JPanel providerSection = new JPanel(new BorderLayout(0, 6));
        providerSection.setBorder(new TitledBorder("Reranker-Provider (Override)"));
        overwriteBox = new JCheckBox(
                "Eigene Reranker-Konfiguration verwenden (Override)");
        overwriteBox.setToolTipText(
                "Wenn deaktiviert, werden die Reranker-spezifischen Felder aus dem Tab \"Allgemein\" "
                        + "des dort gewählten Providers verwendet. Wenn aktiviert, kann hier ein anderer "
                        + "Provider und für diesen Provider alle für Reranking nötigen Felder "
                        + "(URL, Modell, API-Key, Auth, Ports, …) abweichend gesetzt werden.");
        providerSection.add(overwriteBox, BorderLayout.NORTH);

        overwriteInfoLabel = new JLabel();
        overwriteInfoLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        overwriteInfoLabel.setForeground(new Color(96, 96, 96));
        overwriteInfoLabel.setFont(overwriteInfoLabel.getFont().deriveFont(Font.PLAIN, 11f));

        providerPanel = new ProviderConfigPanel(EnumSet.of(Facet.RERANKER));
        JPanel providerInner = new JPanel(new BorderLayout(0, 4));
        providerInner.add(overwriteInfoLabel, BorderLayout.NORTH);
        providerInner.add(providerPanel, BorderLayout.CENTER);
        providerSection.add(providerInner, BorderLayout.CENTER);
        mainPanel.add(providerSection);

        // ── Retrieval-Parameter ───────────────────────────────────
        JPanel retrPanel = new JPanel(new GridBagLayout());
        retrPanel.setBorder(new TitledBorder("Retrieval-Parameter"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        retrPanel.add(new JLabel("Top-N (finale Ergebnisse):"), gbc);
        gbc.gridx = 1;
        topNSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        retrPanel.add(topNSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        retrPanel.add(new JLabel("Kandidaten-Pool:"), gbc);
        gbc.gridx = 1;
        candidatePoolSpinner = new JSpinner(new SpinnerNumberModel(50, 5, 200, 5));
        retrPanel.add(candidatePoolSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        retrPanel.add(new JLabel("Score-Schwellwert (0.0–1.0):"), gbc);
        gbc.gridx = 1;
        scoreThresholdField = new JTextField("0.0", 8);
        retrPanel.add(scoreThresholdField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        retrPanel.add(new JLabel("Timeout (Sekunden):"), gbc);
        gbc.gridx = 1;
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 120, 5));
        retrPanel.add(timeoutSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        useProxyBox.setToolTipText(
                "Wenn aktiv, werden Modell-Abruf und Verbindungstest über den global "
                        + "konfigurierten Proxy geleitet. Aus = direkt (DIRECT).");
        retrPanel.add(useProxyBox, gbc);
        gbc.gridy = 5;
        JPanel proxyExtras = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        proxyExtras.add(useProxyAuthBox);
        proxyExtras.add(useE2eBox);
        retrPanel.add(proxyExtras, gbc);
        mainPanel.add(retrPanel);

        // ── Verbindungstest ───────────────────────────────────────
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testPanel.setBorder(new TitledBorder("Verbindungstest"));
        testButton = new JButton("🔍 Verbindung testen");
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        testButton.addActionListener(e -> testConnection());
        testPanel.add(testButton);
        testPanel.add(statusLabel);
        mainPanel.add(testPanel);

        // ── Info ──────────────────────────────────────────────────
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(new TitledBorder("Hinweise"));
        JTextArea infoText = new JTextArea(
                "Reranker (Cross-Encoder) verbessert RAG-Treffer durch präzises\n"
                        + "Re-Scoring von Frage + Passage. Pipeline-Reihenfolge:\n"
                        + "  ① BM25 → Kandidaten-Pool\n"
                        + "  ② (optional) Embeddings → erweiterter Pool\n"
                        + "  ③ Reranker → präzises Re-Scoring → Top-N\n\n"
                        + "Empfohlene Modelle:\n"
                        + "• BAAI/bge-reranker-v2-m3 (multilingual, schnell)\n"
                        + "• BAAI/bge-reranker-v2-gemma (höchste Qualität)\n"
                        + "• cross-encoder/ms-marco-MiniLM-L-6-v2 (English, sehr schnell)\n\n"
                        + "💡 Proxy-Einstellungen werden aus dem Proxy-Tab übernommen.");
        infoText.setEditable(false);
        infoText.setBackground(getBackground());
        infoText.setFont(infoText.getFont().deriveFont(Font.PLAIN, 11f));
        infoPanel.add(infoText, BorderLayout.CENTER);
        mainPanel.add(infoPanel);

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        overwriteBox.addActionListener(e -> applyOverwriteState());
        loadFromSettings();
        applyOverwriteState();
    }

    private void applyOverwriteState() {
        boolean on = overwriteBox.isSelected();
        setEnabledRecursive(providerPanel, on);
        overwriteInfoLabel.setText(on
                ? "Override aktiv: Sämtliche reranker-relevanten Felder stammen aus diesem Tab."
                : "Override inaktiv: Reranker-Felder stammen aus den Reranker-spezifischen Einstellungen unter \"Allgemein\".");
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
        Map<String, String> cfg = s.rerankerConfig != null
                ? s.rerankerConfig : new HashMap<String, String>();

        enabledBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("enabled", "false")));
        overwriteBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("overwrite", "false")));

        try { topNSpinner.setValue(Integer.parseInt(cfg.getOrDefault("topN", "5"))); }
        catch (NumberFormatException e) { /* keep */ }
        try { candidatePoolSpinner.setValue(Integer.parseInt(cfg.getOrDefault("candidatePoolSize", "50"))); }
        catch (NumberFormatException e) { /* keep */ }
        try { timeoutSpinner.setValue(Integer.parseInt(cfg.getOrDefault("timeout", "30"))); }
        catch (NumberFormatException e) { /* keep */ }
        scoreThresholdField.setText(cfg.getOrDefault("scoreThreshold", "0.0"));
        useProxyBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("useProxy", "false")));
        useProxyAuthBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("useProxyAuth", "false")));
        useE2eBox.setSelected(Boolean.parseBoolean(cfg.getOrDefault("useE2e", "false")));

        providerPanel.loadFromConfig(cfg);
    }

    @Override
    protected void applyToSettings(Settings s) {
        if (s.rerankerConfig == null) s.rerankerConfig = new HashMap<String, String>();
        // Strip stale custom-header keys so deleted Custom-mode headers actually disappear.
        s.rerankerConfig.keySet().removeIf(k -> k != null && k.startsWith("custom.header."));
        // Drop legacy flat keys — they no longer represent the truth after the refactor.
        s.rerankerConfig.remove("apiUrl");
        s.rerankerConfig.remove("model");
        s.rerankerConfig.remove("apiKey");

        s.rerankerConfig.putAll(providerPanel.saveToConfig());
        s.rerankerConfig.put("enabled", String.valueOf(enabledBox.isSelected()));
        s.rerankerConfig.put("overwrite", String.valueOf(overwriteBox.isSelected()));
        s.rerankerConfig.put("topN", topNSpinner.getValue().toString());
        s.rerankerConfig.put("candidatePoolSize", candidatePoolSpinner.getValue().toString());
        s.rerankerConfig.put("timeout", timeoutSpinner.getValue().toString());
        s.rerankerConfig.put("scoreThreshold", scoreThresholdField.getText().trim());
        s.rerankerConfig.put("useProxy", String.valueOf(useProxyBox.isSelected()));
        s.rerankerConfig.put("useProxyAuth", String.valueOf(useProxyAuthBox.isSelected()));
        s.rerankerConfig.put("useE2e", String.valueOf(useE2eBox.isSelected()));
    }

    @Override
    protected void afterApply(Settings s) {
        // Live-update the RagService reranker client with the freshly persisted config.
        // Guarded so we never force a cold start of the RAG pipeline just because the
        // user clicked Apply in settings — if RAG isn't running yet, the next real
        // RagService.getInstance() call will pick up the new config via fromStoredConfig().
        if (!de.bund.zrb.rag.service.RagService.isInitialized()) return;
        try {
            RerankerSettings rs = RerankerSettings.fromStoredConfig();
            de.bund.zrb.rag.service.RagService.getInstance().updateRerankerSettings(rs);
        } catch (Exception e) {
            // Ignore — never break settings save on a live-update glitch.
        }
    }

    /**
     * Exposes the "Reranker aktivieren"-Checkbox so it can be reparented into a host
     * panel (the parent {@code AiSettingsPanel} tab bar).
     */
    public JCheckBox getEnabledBox() {
        return enabledBox;
    }

    /**
     * Tests the currently entered configuration without persisting it: builds a
     * {@link RerankerSettings} from the in-memory UI snapshot + the persisted
     * {@code aiConfig} and runs a minimal rerank query against it.
     */
    private void testConnection() {
        statusLabel.setText("⏳ Teste Verbindung...");
        statusLabel.setForeground(Color.GRAY);

        // Snapshot UI state into a fresh map (independent of SettingsHelper).
        final Map<String, String> snapshot = new HashMap<String, String>();
        snapshot.putAll(providerPanel.saveToConfig());
        snapshot.put("enabled", String.valueOf(enabledBox.isSelected()));
        snapshot.put("overwrite", String.valueOf(overwriteBox.isSelected()));
        snapshot.put("topN", topNSpinner.getValue().toString());
        snapshot.put("candidatePoolSize", candidatePoolSpinner.getValue().toString());
        snapshot.put("timeout", timeoutSpinner.getValue().toString());
        snapshot.put("scoreThreshold", scoreThresholdField.getText().trim());
        snapshot.put("useProxy", String.valueOf(useProxyBox.isSelected()));
        snapshot.put("useProxyAuth", String.valueOf(useProxyAuthBox.isSelected()));
        snapshot.put("useE2e", String.valueOf(useE2eBox.isSelected()));

        final Map<String, String> aiCfg = SettingsHelper.load().aiConfig;

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                RerankerSettings rs = RerankerSettings.fromConfig(snapshot, aiCfg);
                rs.setEnabled(true);
                HttpRerankerClient client = new HttpRerankerClient(rs);
                try {
                    float[] scores = client.rerank(
                            "Was ist maschinelles Lernen?",
                            Arrays.asList(
                                    "Maschinelles Lernen ist ein Teilgebiet der künstlichen Intelligenz.",
                                    "Das Wetter in Berlin ist heute sonnig."));
                    if (scores.length >= 2 && scores[0] > scores[1]) {
                        return String.format("✅ Funktioniert! Score relevant=%.3f, irrelevant=%.3f",
                                scores[0], scores[1]);
                    } else if (scores.length >= 2) {
                        return String.format("⚠️ Antwort erhalten, Ranking unerwartet: [%.3f, %.3f]",
                                scores[0], scores[1]);
                    } else {
                        return "⚠️ Unerwartete Antwort: " + scores.length + " Scores";
                    }
                } catch (RerankerClient.RerankerException ex) {
                    return "❌ Fehler: " + ex.getMessage();
                } catch (Exception ex) {
                    return "❌ " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String r = get();
                    statusLabel.setText(r);
                    statusLabel.setForeground(
                            r.startsWith("✅") ? new Color(0, 128, 0)
                            : r.startsWith("⚠") ? new Color(200, 150, 0)
                            : Color.RED);
                } catch (Exception e) {
                    statusLabel.setText("❌ " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                }
            }
        };
        worker.execute();
    }

    /** Internal getter retained for compatibility with potential external test hooks. */
    @SuppressWarnings("unused")
    private JComponent getProviderPanel() { return providerPanel; }
}

