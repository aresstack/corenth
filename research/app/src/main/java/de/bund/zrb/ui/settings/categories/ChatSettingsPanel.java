package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.components.ChatMode;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.ui.settings.ModeToolsetDialog;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Chat-specific settings (per-mode tool contract, response language,
 * AI editor appearance, JSON formatting). Technical AI provider and
 * encryption/proxy settings live in {@link AiSettingsPanel}.
 */
public class ChatSettingsPanel extends AbstractSettingsPanel {

    /** Per-mode UI controls (one set per ChatMode tab). */
    private static final class ModeTabControls {
        final JCheckBox toolsetSwitchBox;
        final JButton toolsetButton;
        final JTextArea prefix;
        final JTextArea postfix;
        ModeTabControls(JCheckBox toolsetSwitchBox, JButton toolsetButton,
                        JTextArea prefix, JTextArea postfix) {
            this.toolsetSwitchBox = toolsetSwitchBox;
            this.toolsetButton = toolsetButton;
            this.prefix = prefix;
            this.postfix = postfix;
        }
    }

    /**
     * In-memory buffer for aiConfig keys that are edited via sub-dialogs
     * (ModeToolsetDialog) and must survive the
     * {@code settings = SettingsHelper.load()} in {@code apply()}.
     */
    private final Map<String, String> pendingAiConfig = new LinkedHashMap<>();

    private final Map<ChatMode, ModeTabControls> modeControls = new LinkedHashMap<>();

    private final JComboBox<String> aiLanguageCombo;
    private final JComboBox<String> aiEditorFontCombo, aiEditorFontSizeCombo;
    private final JSpinner aiEditorHeightSpinner;
    private final JCheckBox wrapJsonBox, prettyJsonBox;
    private final JComboBox<String> attachmentModeCombo;

    public ChatSettingsPanel() {
        super("chat", "Chat");

        // Seed pendingAiConfig with all existing toolset/toolsetSwitch/prefix/postfix keys
        // so they survive the SettingsHelper.load() in apply()
        for (Map.Entry<String, String> entry : settings.aiConfig.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("toolset.") || key.startsWith("toolsetSwitch.")
                    || key.startsWith("toolPrefix.") || key.startsWith("toolPostfix.")) {
                pendingAiConfig.put(key, entry.getValue());
            }
        }

        FormBuilder fb = new FormBuilder();

        // ── Per-mode tool contract: one tab per ChatMode ──
        fb.addSection("Mode-spezifische Einstellungen");
        JTabbedPane modeTabs = new JTabbedPane(JTabbedPane.TOP);
        for (ChatMode mode : ChatMode.values()) {
            if (mode == ChatMode.AGENT_PLUGIN) continue; // dynamic / not user-configurable
            modeTabs.addTab(mode.getLabel(), buildModeTab(mode));
            int idx = modeTabs.getTabCount() - 1;
            if (mode.getTooltip() != null && !mode.getTooltip().isEmpty()) {
                modeTabs.setToolTipTextAt(idx, mode.getTooltip());
            }
        }
        fb.addWide(modeTabs);

        aiLanguageCombo = new JComboBox<>(new String[]{"Deutsch (Standard)", "Keine Vorgabe", "Englisch"});
        String lang = settings.aiConfig.getOrDefault("assistant.language", "de").trim().toLowerCase();
        if (lang.isEmpty() || "none".equals(lang)) aiLanguageCombo.setSelectedItem("Keine Vorgabe");
        else if ("en".equals(lang) || "english".equals(lang)) aiLanguageCombo.setSelectedItem("Englisch");
        else aiLanguageCombo.setSelectedItem("Deutsch (Standard)");
        fb.addRow("Antwortsprache:", aiLanguageCombo);

        // ── Anhang-Modus ──
        fb.addSection("Anhänge im Chat");
        attachmentModeCombo = new JComboBox<>(new String[]{
                "Auto (RAG, mit Volltext-Fallback)",
                "Direkt anhängen (Volltext)",
                "Komprimiert (nur RAG/Top-K-Chunks)"
        });
        attachmentModeCombo.setToolTipText("<html>"
                + "<b>Auto</b> — Wenn RAG/Embeddings konfiguriert und indexiert: nur die relevantesten "
                + "Chunks. Sonst: vollständiger Dokumenttext. (Empfohlen.)<br>"
                + "<b>Direkt anhängen</b> — Der vollständige Dokumenttext (Markdown) wird "
                + "immer in den Prompt eingebettet. Robust, aber tokenintensiv.<br>"
                + "<b>Komprimiert</b> — Nur Top-K relevante Chunks aus RAG. "
                + "Tokenarm, aber funktioniert nur mit konfiguriertem Embedding-Modell."
                + "</html>");
        String mode = settings.aiConfig.getOrDefault("attachment.mode", "AUTO").trim().toUpperCase();
        if ("FULL".equals(mode)) attachmentModeCombo.setSelectedIndex(1);
        else if ("RAG".equals(mode)) attachmentModeCombo.setSelectedIndex(2);
        else attachmentModeCombo.setSelectedIndex(0);
        fb.addRow("Anhang-Modus:", attachmentModeCombo);

        fb.addSection("KI-Editor");

        aiEditorFontCombo = new JComboBox<>(new String[]{"Monospaced", "Consolas", "Courier New", "Dialog", "Menlo"});
        aiEditorFontCombo.setSelectedItem(settings.aiConfig.getOrDefault("editor.font", "Monospaced"));
        fb.addRow("Schriftart:", aiEditorFontCombo);

        aiEditorFontSizeCombo = new JComboBox<>(new String[]{"10","11","12","13","14","16","18","20","24","28","32"});
        aiEditorFontSizeCombo.setEditable(true);
        aiEditorFontSizeCombo.setSelectedItem(settings.aiConfig.getOrDefault("editor.fontSize", "12"));
        fb.addRow("Schriftgröße:", aiEditorFontSizeCombo);

        aiEditorHeightSpinner = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(settings.aiConfig.getOrDefault("editor.lines", "3")), 1, 1000, 1));
        fb.addRow("Editor-Höhe (Zeilen):", aiEditorHeightSpinner);

        wrapJsonBox = new JCheckBox("JSON als Markdown-Codeblock einrahmen");
        wrapJsonBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("wrapjson", "true")));
        fb.addWide(wrapJsonBox);

        prettyJsonBox = new JCheckBox("JSON schön formatieren (Pretty-Print)");
        prettyJsonBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("prettyjson", "true")));
        fb.addWide(prettyJsonBox);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.aiConfig.put("editor.font", Objects.toString(aiEditorFontCombo.getSelectedItem(), "Monospaced"));
        s.aiConfig.put("editor.fontSize", Objects.toString(aiEditorFontSizeCombo.getSelectedItem(), "12"));
        s.aiConfig.put("editor.lines", aiEditorHeightSpinner.getValue().toString());

        // Persist prefix/postfix/toolsetSwitch for ALL modes (one per tab)
        for (Map.Entry<ChatMode, ModeTabControls> entry : modeControls.entrySet()) {
            ChatMode mode = entry.getKey();
            ModeTabControls ctl = entry.getValue();
            s.aiConfig.put("toolPrefix." + mode.name(), ctl.prefix.getText().trim());
            s.aiConfig.put("toolPostfix." + mode.name(), ctl.postfix.getText().trim());
            ModeToolsetDialog.setToolsetSwitchingEnabled(s.aiConfig, mode, ctl.toolsetSwitchBox.isSelected());
        }
        s.aiConfig.remove("toolPrefix"); s.aiConfig.remove("toolPostfix");

        // Persist remaining toolset.* keys from the pendingAiConfig buffer
        for (Map.Entry<String, String> entry : pendingAiConfig.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("toolset.")) {
                s.aiConfig.put(key, entry.getValue());
            }
        }

        String selectedLanguage = Objects.toString(aiLanguageCombo.getSelectedItem(), "Deutsch (Standard)");
        if ("Keine Vorgabe".equals(selectedLanguage)) s.aiConfig.put("assistant.language", "none");
        else if ("Englisch".equals(selectedLanguage)) s.aiConfig.put("assistant.language", "en");
        else s.aiConfig.put("assistant.language", "de");

        s.aiConfig.put("wrapjson", String.valueOf(wrapJsonBox.isSelected()));
        s.aiConfig.put("prettyjson", String.valueOf(prettyJsonBox.isSelected()));

        int idx = attachmentModeCombo.getSelectedIndex();
        String attachmentMode = (idx == 1) ? "FULL" : (idx == 2) ? "RAG" : "AUTO";
        s.aiConfig.put("attachment.mode", attachmentMode);
    }

    /**
     * Builds a settings tab for a single {@link ChatMode}, registering its controls
     * in {@link #modeControls} for later save in {@link #applyToSettings(Settings)}.
     */
    private JPanel buildModeTab(final ChatMode mode) {
        FormBuilder fb = new FormBuilder();

        if (mode.getTooltip() != null && !mode.getTooltip().isEmpty()) {
            fb.addInfo("<html>" + escapeHtml(mode.getTooltip()) + "</html>");
        }

        // Toolset switching
        final JCheckBox toolsetSwitchBox = new JCheckBox("Tools beim Mode-Wechsel aktualisieren");
        toolsetSwitchBox.setToolTipText("Wenn aktiviert, werden beim Umschalten in diesen Mode\n"
                + "nur die ausgewählten Tools dem Bot zur Verfügung gestellt.");
        final JButton toolsetButton = new JButton("🔧 Tools auswählen…");
        toolsetButton.setToolTipText("Verfügbare Tools für diesen Mode konfigurieren");
        toolsetButton.addActionListener(e -> ModeToolsetDialog.show(toolsetButton, mode, pendingAiConfig));
        toolsetSwitchBox.addActionListener(e -> {
            toolsetButton.setEnabled(toolsetSwitchBox.isSelected());
            ModeToolsetDialog.setToolsetSwitchingEnabled(pendingAiConfig, mode, toolsetSwitchBox.isSelected());
        });
        JPanel toolsetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        toolsetRow.add(toolsetSwitchBox);
        toolsetRow.add(toolsetButton);
        fb.addWide(toolsetRow);

        // Prefix
        final JTextArea prefixArea = new JTextArea(3, 30);
        prefixArea.setLineWrap(true); prefixArea.setWrapStyleWord(true);
        JButton prefixResetBtn = new JButton("↺");
        prefixResetBtn.setToolTipText("Prefix auf Default zurücksetzen");
        prefixResetBtn.setMargin(new Insets(2, 6, 2, 6));
        prefixResetBtn.addActionListener(e -> prefixArea.setText(mode.getDefaultToolPrefix()));
        fb.addRowWithButton("KI-Prefix:", new JScrollPane(prefixArea), prefixResetBtn);

        // Postfix
        final JTextArea postfixArea = new JTextArea(2, 30);
        postfixArea.setLineWrap(true); postfixArea.setWrapStyleWord(true);
        JButton postfixResetBtn = new JButton("↺");
        postfixResetBtn.setToolTipText("Postfix auf Default zurücksetzen");
        postfixResetBtn.setMargin(new Insets(2, 6, 2, 6));
        postfixResetBtn.addActionListener(e -> postfixArea.setText(mode.getDefaultToolPostfix()));
        fb.addRowWithButton("KI-Postfix:", new JScrollPane(postfixArea), postfixResetBtn);

        JButton resetAllBtn = new JButton("Alles auf Default zurücksetzen");
        resetAllBtn.addActionListener(e -> {
            prefixArea.setText(mode.getDefaultToolPrefix());
            postfixArea.setText(mode.getDefaultToolPostfix());
        });
        fb.addButtons(resetAllBtn);

        // Initial values from settings
        String prefixKey = "toolPrefix." + mode.name();
        String postfixKey = "toolPostfix." + mode.name();
        prefixArea.setText(pendingAiConfig.containsKey(prefixKey)
                ? pendingAiConfig.get(prefixKey)
                : settings.aiConfig.getOrDefault(prefixKey, mode.getDefaultToolPrefix()));
        postfixArea.setText(pendingAiConfig.containsKey(postfixKey)
                ? pendingAiConfig.get(postfixKey)
                : settings.aiConfig.getOrDefault(postfixKey, mode.getDefaultToolPostfix()));

        boolean switchEnabled = ModeToolsetDialog.isToolsetSwitchingEnabled(pendingAiConfig, mode)
                || ModeToolsetDialog.isToolsetSwitchingEnabled(settings.aiConfig, mode);
        toolsetSwitchBox.setSelected(switchEnabled);
        toolsetButton.setEnabled(switchEnabled);

        modeControls.put(mode, new ModeTabControls(toolsetSwitchBox, toolsetButton, prefixArea, postfixArea));
        return fb.getPanel();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

