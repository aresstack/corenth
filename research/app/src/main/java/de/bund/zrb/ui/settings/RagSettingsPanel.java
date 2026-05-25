package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.settings.provider.Facet;
import de.bund.zrb.ui.settings.provider.ProviderConfigPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings panel for RAG/Embedding configuration.
 *
 * <p>Liefert ausschließlich einen <b>Override</b> für den Embedding-Provider:
 * Provider, Modell und (sofern vorhanden) URL können hier abweichend vom Tab
 * "Allgemein" gesetzt werden. Sämtliche übrigen Provider-Felder
 * (API-Key, Auth, Ports, …) werden weiterhin aus "Allgemein" übernommen.</p>
 *
 * <p>Timeout, Batch-Größe und der "RAG aktiviert"-Schalter sind <b>keine</b>
 * Overrides, sondern eigenständige Embedding-Einstellungen.</p>
 */
public class RagSettingsPanel extends JPanel {

    private final ProviderConfigPanel providerPanel;
    private final JCheckBox enabledCheckbox;
    private final JCheckBox overwriteCheckbox;
    private final JLabel overwriteInfoLabel;
    private final JSpinner timeoutSpinner;
    private final JSpinner batchSizeSpinner;
    private final JCheckBox useProxyBox;
    private final JCheckBox useProxyAuthBox;
    private final JCheckBox useE2eBox;
    private final JLabel proxyInfoLabel;

    public RagSettingsPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Vorab anlegen, damit der Supplier sie referenzieren kann (Konstruktor-Reihenfolge).
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

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("RAG & Embedding-Konfiguration");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        HelpButton helpButton = new HelpButton("Was ist RAG?",
                e -> HelpContentProvider.showHelpPopup(
                        (Component) e.getSource(),
                        HelpContentProvider.HelpTopic.SETTINGS_RAG));
        helpButton.setVisible(SettingsHelper.load().showHelpIcons);
        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        helpPanel.add(helpButton);
        headerPanel.add(helpPanel, BorderLayout.EAST);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(headerPanel);

        // RAG-Status
        JPanel enablePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enablePanel.setBorder(new TitledBorder("RAG-Status"));
        enabledCheckbox = new JCheckBox("RAG/Embeddings aktiviert");
        enabledCheckbox.setSelected(true);
        enablePanel.add(enabledCheckbox);
        mainPanel.add(enablePanel);

        // Provider-Override
        JPanel providerSection = new JPanel(new BorderLayout(0, 6));
        providerSection.setBorder(new TitledBorder("Embedding-Provider (Override)"));

        overwriteCheckbox = new JCheckBox(
                "Eigene Embedding-Konfiguration verwenden (Override)");
        overwriteCheckbox.setToolTipText(
                "Wenn deaktiviert, werden die Embedding-spezifischen Felder aus dem Tab \"Allgemein\" "
                        + "des dort gewählten Providers verwendet. Wenn aktiviert, können hier ein "
                        + "<b>anderer</b> Provider und für diesen Provider alle für Embeddings nötigen "
                        + "Felder (URL, Modell, API-Key, Auth, Ports, …) abweichend gesetzt werden. "
                        + "Chat-/Reranker-/Audio-spezifische Felder werden NICHT überschrieben.");
        providerSection.add(overwriteCheckbox, BorderLayout.NORTH);

        overwriteInfoLabel = new JLabel();
        overwriteInfoLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        overwriteInfoLabel.setForeground(new Color(96, 96, 96));
        overwriteInfoLabel.setFont(overwriteInfoLabel.getFont().deriveFont(Font.PLAIN, 11f));

        providerPanel = new ProviderConfigPanel(EnumSet.of(Facet.EMBEDDINGS),
                () -> useProxyBox.isSelected());

        JPanel providerInner = new JPanel(new BorderLayout(0, 4));
        providerInner.add(overwriteInfoLabel, BorderLayout.NORTH);
        providerInner.add(providerPanel, BorderLayout.CENTER);
        providerSection.add(providerInner, BorderLayout.CENTER);

        mainPanel.add(providerSection);

        // Performance (KEIN Override — eigenständige Embedding-Settings)
        JPanel perfPanel = new JPanel(new GridBagLayout());
        perfPanel.setBorder(new TitledBorder("Performance"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        perfPanel.add(new JLabel("Timeout (Sekunden):"), gbc);
        gbc.gridx = 1;
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 300, 5));
        perfPanel.add(timeoutSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        perfPanel.add(new JLabel("Batch-Größe:"), gbc);
        gbc.gridx = 1;
        batchSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        perfPanel.add(batchSizeSpinner, gbc);

        mainPanel.add(perfPanel);

        // Proxy-Hinweis
        JPanel proxyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        proxyPanel.setBorder(new TitledBorder("Proxy"));
        proxyPanel.add(useProxyBox);
        proxyPanel.add(useProxyAuthBox);
        proxyPanel.add(useE2eBox);
        proxyInfoLabel = new JLabel();
        updateProxyInfoLabel();
        proxyPanel.add(proxyInfoLabel);

        JButton refreshProxyButton = new JButton("🔄 Aktualisieren");
        refreshProxyButton.addActionListener(e -> updateProxyInfoLabel());
        proxyPanel.add(refreshProxyButton);

        mainPanel.add(proxyPanel);

        // Info
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(new TitledBorder("Hinweise"));
        JTextArea infoText = new JTextArea(
                "RAG (Retrieval-Augmented Generation) verbessert KI-Antworten\n"
                        + "durch Zugriff auf Ihre Dokumente.\n\n"
                        + "Embeddings wandeln Text in Vektoren um, die Bedeutung erfassen.\n"
                        + "So findet das System auch konzeptuell verwandte Passagen.\n\n"
                        + "Empfohlene Embedding-Modelle:\n"
                        + "• Ollama: nomic-embed-text, all-minilm\n"
                        + "• OpenAI: text-embedding-3-small\n\n"
                        + "💡 Proxy-Einstellungen werden aus dem Proxy-Tab übernommen."
        );
        infoText.setEditable(false);
        infoText.setBackground(getBackground());
        infoText.setFont(infoText.getFont().deriveFont(Font.PLAIN, 11f));
        infoPanel.add(infoText, BorderLayout.CENTER);
        mainPanel.add(infoPanel);

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        overwriteCheckbox.addActionListener(e -> applyOverwriteState());

        loadSettings();
        applyOverwriteState();
    }

    private void applyOverwriteState() {
        boolean on = overwriteCheckbox.isSelected();
        setEnabledRecursive(providerPanel, on);
        overwriteInfoLabel.setText(on
                ? "Override aktiv: Sämtliche embedding-relevanten Felder (inkl. Credentials) stammen aus diesem Tab."
                : "Override inaktiv: Embedding-Felder stammen aus den Embedding-spezifischen Einstellungen unter \"Allgemein\".");
    }

    private static void setEnabledRecursive(Component c, boolean enabled) {
        c.setEnabled(enabled);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                setEnabledRecursive(child, enabled);
            }
        }
    }

    private void updateProxyInfoLabel() {
        Settings settings = SettingsHelper.load();
        String mode = settings.proxyMode;
        if ("MANUAL".equals(mode) && settings.proxyHost != null && !settings.proxyHost.trim().isEmpty()) {
            proxyInfoLabel.setText("✅ Proxy konfiguriert: " + settings.proxyHost + ":" + settings.proxyPort + " (MANUAL)");
            proxyInfoLabel.setForeground(new Color(0, 128, 0));
        } else {
            proxyInfoLabel.setText("✅ Proxy konfiguriert (PAC/WPAD)");
            proxyInfoLabel.setForeground(new Color(0, 128, 0));
        }
    }

    private void loadSettings() {
        Settings settings = SettingsHelper.load();
        Map<String, String> embConfig = settings.embeddingConfig;
        if (embConfig == null) embConfig = new HashMap<String, String>();

        enabledCheckbox.setSelected(Boolean.parseBoolean(embConfig.getOrDefault("enabled", "true")));
        overwriteCheckbox.setSelected(Boolean.parseBoolean(embConfig.getOrDefault("overwrite", "false")));
        useProxyBox.setSelected(Boolean.parseBoolean(embConfig.getOrDefault("useProxy", "false")));
        useProxyAuthBox.setSelected(Boolean.parseBoolean(embConfig.getOrDefault("useProxyAuth", "false")));
        useE2eBox.setSelected(Boolean.parseBoolean(embConfig.getOrDefault("useE2e", "false")));

        providerPanel.loadFromConfig(embConfig);

        try {
            timeoutSpinner.setValue(Integer.parseInt(embConfig.getOrDefault("timeout", "30")));
        } catch (NumberFormatException e) { /* ignore */ }
        try {
            batchSizeSpinner.setValue(Integer.parseInt(embConfig.getOrDefault("batchSize", "10")));
        } catch (NumberFormatException e) { /* ignore */ }
    }

    /** Speichert die aktuellen Einstellungen in das Settings-Objekt. */
    public void saveToSettings(Settings settings) {
        if (settings.embeddingConfig == null) {
            settings.embeddingConfig = new HashMap<String, String>();
        }
        // Stale cloud.header.*-Einträge entfernen, damit gelöschte Custom-Header
        // tatsächlich verschwinden (das Provider-Panel besitzt diesen Namespace).
        settings.embeddingConfig.keySet().removeIf(k -> k.startsWith("cloud.header."));
        settings.embeddingConfig.putAll(providerPanel.saveToConfig());
        settings.embeddingConfig.put("enabled", String.valueOf(enabledCheckbox.isSelected()));
        settings.embeddingConfig.put("overwrite", String.valueOf(overwriteCheckbox.isSelected()));
        settings.embeddingConfig.put("useProxy", String.valueOf(useProxyBox.isSelected()));
        settings.embeddingConfig.put("useProxyAuth", String.valueOf(useProxyAuthBox.isSelected()));
        settings.embeddingConfig.put("useE2e", String.valueOf(useE2eBox.isSelected()));
        settings.embeddingConfig.put("timeout", String.valueOf(timeoutSpinner.getValue()));
        settings.embeddingConfig.put("batchSize", String.valueOf(batchSizeSpinner.getValue()));
    }

    /** Gibt zurück, ob RAG aktiviert ist. */
    public boolean isEnabled() {
        return enabledCheckbox.isSelected();
    }

    /**
     * Exposes the internal "Aktiviert"-Checkbox so it can be reparented into a host panel
     * (e.g. the parent AI-Settings tab bar).
     */
    public JCheckBox getEnabledBox() {
        return enabledCheckbox;
    }
}

