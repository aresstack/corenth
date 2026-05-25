package de.bund.zrb.ui.settings.categories;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;
import de.bund.zrb.net.ModelDownloader;
import de.bund.zrb.net.ModelManifest;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.ui.settings.provider.Facet;
import de.bund.zrb.ui.settings.provider.ProviderCardRenderer;
import de.bund.zrb.ui.settings.provider.ProviderDefinitions;
import de.bund.zrb.util.ExecutableLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Technical AI settings: provider selection (Ollama, Cloud, Llama.cpp, ONNX Runtime),
 * proxy authentication and end-to-end encryption.
 * Chat-related settings (modes, prefix/postfix, response language, editor appearance,
 * JSON formatting) are in {@link ChatSettingsPanel}.
 */
public class AiSettingsPanel extends AbstractSettingsPanel {

    private static final Logger LOG = Logger.getLogger(AiSettingsPanel.class.getName());

    /** ONNX model manifest loaded from classpath resource. {@code null} if not available. */
    private static final ModelManifest ONNX_MANIFEST = loadManifest("/model-manifest.json");

    private final JComboBox<AiProvider> providerCombo;

    /**
     * Per-Tab Proxy-Schalter: steuert, ob Modell-Abrufe (🔄) und Verbindungstests (🧪)
     * in diesem Tab über den global konfigurierten Proxy oder direkt (DIRECT) gehen.
     * Persistiert als {@code aiConfig["useProxy"]}.
     */
    private JCheckBox useProxyBox;
    /** Per-Tab Schalter: Basic-Auth-Credentials (im Proxy-Tab gepflegt) für diesen KI-Tab verwenden. */
    private JCheckBox useProxyAuthBox;
    /** Per-Tab Schalter: E2E-Verschlüsselung (Passwort im Proxy-Tab gepflegt) für diesen KI-Tab verwenden. */
    private JCheckBox useE2eBox;


    // Ollama
    private final JTextField ollamaUrlField, ollamaKeepAliveField;
    private final JComboBox<String> ollamaModelCombo;
    private JLabel ollamaModelStatusLabel;
    /** Optionaler Override für den Modell-Listen-Pfad (relativ zur Ollama-Base-URL). */
    private JTextField ollamaModelsEndpointField;
    // Ollama embeddings & reranker (same URL, different models)
    private final JComboBox<String> ollamaEmbeddingsModelCombo;
    private final JComboBox<String> ollamaRerankModelCombo;
    // Cloud
    private final JComboBox<String> cloudProviderField;
    private final JTextField cloudApiKeyField, cloudApiUrlField;
    private final JComboBox<String> cloudModelCombo;    private JLabel cloudModelStatusLabel;
    private final JTextField cloudAuthHeaderField, cloudAuthPrefixField, cloudApiVersionField;
    private final JTextField cloudOrgField, cloudProjectField;
    // Public Cloud embeddings & reranker (own URL and model per task)
    private final JTextField cloudEmbeddingsUrlField, cloudRerankUrlField;
    private final JComboBox<String> cloudEmbeddingsModelCombo, cloudRerankModelCombo;
    // Public Cloud audio (TTS)
    private final JTextField cloudAudioUrlField;
    private final JComboBox<String> cloudAudioModelCombo;
    // Public Cloud Responses API (stateful — POST + GET-by-ID)
    private final JTextField cloudResponsesUrlField, cloudResponsesByIdUrlField;
    /** Optionales Override für die Modell-Listen-URL (leer ⇒ Auto-Ableitung aus cloud.url). */
    private JTextField cloudModelsUrlField;
    // OpenAI Compatible (slim: just base URL, API key, models for chat/embeddings/rerank, editable endpoints)
    private final JTextField oaicBaseUrlField, oaicApiKeyField;
    private final JComboBox<String> oaicModelCombo;
    private final JComboBox<String> oaicEmbeddingsModelCombo;
    private final JComboBox<String> oaicRerankModelCombo;
    private final JComboBox<String> oaicAudioModelCombo;
    private JLabel oaicModelStatusLabel;
    private final java.util.Map<String, JTextField> oaicEndpointFields = new java.util.LinkedHashMap<>();
    // Custom (vollständig manuell: base URL + Modelle (Chat/Embeddings/Rerank) + Endpunkte + frei definierbare HTTP-Header)
    private final JTextField customBaseUrlField;
    private final JComboBox<String> customModelCombo;
    private final JComboBox<String> customEmbeddingsModelCombo;
    private final JComboBox<String> customRerankModelCombo;
    private final JComboBox<String> customAudioModelCombo;
    private JLabel customModelStatusLabel;
    private final java.util.Map<String, JTextField> customEndpointFields = new java.util.LinkedHashMap<>();
    private javax.swing.table.DefaultTableModel customHeadersModel;
    private JTable customHeadersTable;
    // Private Cloud outer: mode dropdown (analogous to Public Cloud's vendor dropdown).
    // Two combo instances share the same ComboBoxModel so the selection stays in sync
    // across both sub-cards while keeping the "Modus:" row inside each FormBuilder grid
    // (so labels align with the other rows).
    private JComboBox<String> privateModeCombo;
    private JComboBox<String> privateModeComboCustom;
    /** Default OpenAI-compatible endpoint paths (relative to Base URL). */
    private static final String[][] OAI_ENDPOINT_DEFAULTS = {
            {"models",       "/v1/models",                      "GET Models"},
            {"chat",         "/v1/chat/completions",            "POST Chat Completions"},
            {"responses",    "/v1/responses",                   "POST Responses"},
            {"responseById", "/v1/responses/{response_id}",   "GET Response by ID"},
            {"embeddings",   "/v1/embeddings",                  "POST Embeddings"},
            {"rerank",       "/v1/rerank",                      "POST Rerank"},
            {"audio",        "/v1/audio/speech",                "POST Audio/Speech"},
    };
    // Llama
    private final JCheckBox llamaEnabledBox, llamaStreamingBox;
    private final JTextField llamaBinaryField, llamaModelField, llamaTempField;
    private final JSpinner llamaPortSpinner, llamaThreadsSpinner, llamaContextSpinner;
    // Llama embeddings & reranker (local server — same port, different model names)
    private final JTextField llamaEmbeddingsModelField, llamaRerankModelField;

    // ONNX Runtime
    private final JTextField onnxModelPathField, onnxTemperatureField, onnxTopPField;
    private final JSpinner onnxMaxTokensSpinner, onnxTopKSpinner;
    private final JComboBox<String> onnxExecutionProviderCombo;
    // ONNX embeddings & reranker (separate model directories)
    private final JTextField onnxEmbeddingsModelPathField, onnxRerankModelPathField;

    // LocalAI — datengetriebene Karte via ProviderCardRenderer + ProviderDefinitions.localAi().
    // Felder werden ueber den Persistenz-Key aus der RenderedCard geholt.
    private final ProviderCardRenderer.RenderedCard localAiCard;
    private final JTextField localAiBaseUrlField, localAiModelField;
    private final JTextField localAiEmbeddingsUrlField, localAiRerankUrlField;
    private final JTextField localAiEmbeddingsModelField, localAiRerankModelField;
    private final JTextField localAiAudioUrlField, localAiAudioModelField;

    // Top-level toggles that gray out the per-provider embeddings / reranker rows.
    // These are the same JCheckBox instances that live inside the embedded RagSettingsPanel
    // / RerankerSettingsPanel — reparented above the tab bar so a single control drives
    // the persisted enabled-state AND the UI grayout across every provider card.
    private JCheckBox topEmbeddingsEnabledBox;
    private JCheckBox topRerankEnabledBox;
    private JCheckBox topAudioEnabledBox;
    private JCheckBox topResponsesEnabledBox;
    private final java.util.List<java.awt.Component> embedDependentFields = new java.util.ArrayList<>();
    private final java.util.List<java.awt.Component> rerankDependentFields = new java.util.ArrayList<>();
    private final java.util.List<java.awt.Component> audioDependentFields = new java.util.ArrayList<>();
    private final java.util.List<java.awt.Component> responsesDependentFields = new java.util.ArrayList<>();

    // Nested tabs (Embeddings/Reranker) — moved here from the left-nav so all AI-related
    // settings live in a single tabbed category.
    private de.bund.zrb.ui.settings.RagSettingsPanel embeddingsTab;
    private RerankerSettingsPanel rerankerTab;
    private SummarizerSettingsPanel summarizerTab;

    public AiSettingsPanel() {
        super("ai", "KI");

        FormBuilder fb = new FormBuilder();


        fb.addSection("KI-Provider");

        // Per-Tab Proxy-Schalter früh anlegen, damit Supplier in ProviderCardRenderer.render()
        // ihn referenzieren können (sonst NPE in den Click-Handlern).
        useProxyBox = new JCheckBox("Proxy verwenden");
        useProxyBox.setToolTipText(
                "Wenn aktiv, werden Modell-Abruf und Verbindungstest über den global "
                        + "konfigurierten Proxy geleitet. Aus = direkt (DIRECT).");
        useProxyAuthBox = new JCheckBox("Proxy-Auth");
        useProxyAuthBox.setToolTipText(
                "Wenn aktiv, werden Anfragen mit Basic-Auth Credentials aus dem Proxy-Tab versehen "
                        + "(Authorization-Header). Credentials werden im Proxy-Tab gepflegt.");
        useE2eBox = new JCheckBox("E2E");
        useE2eBox.setToolTipText(
                "Wenn aktiv, werden Anfragen mit AES-256-GCM gegen den eigenen KI-Proxy verschlüsselt. "
                        + "Passwort wird im Proxy-Tab gepflegt.");

        providerCombo = new JComboBox<>();
        providerCombo.addItem(AiProvider.DISABLED); providerCombo.addItem(AiProvider.OLLAMA);
        providerCombo.addItem(AiProvider.CLOUD);
        providerCombo.addItem(AiProvider.PRIVATE_CLOUD);
        providerCombo.addItem(AiProvider.LOCAL_AI);
        providerCombo.addItem(AiProvider.LLAMA_CPP_SERVER); providerCombo.addItem(AiProvider.ONNX_RUNTIME);
        providerCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AiProvider) setText(((AiProvider) value).getDisplayName());
                return c;
            }
        });
        fb.addRow("Provider:", providerCombo);

        JPanel providerOptionsPanel = new JPanel(new CardLayout());
        providerOptionsPanel.add(new JPanel(), AiProvider.DISABLED.name());

        // OLLAMA
        FormBuilder fbOllama = new FormBuilder();
        ollamaUrlField = new JTextField(30); fbOllama.addRow("URL:", ollamaUrlField);
        ollamaModelCombo = new JComboBox<>();
        ollamaModelCombo.setEditable(true);
        JButton ollamaFetchBtn = new JButton("🔄");
        ollamaFetchBtn.setToolTipText("Verfügbare Modelle vom Ollama-Server abrufen");
        ollamaFetchBtn.setMargin(new Insets(2, 4, 2, 4));
        ollamaModelStatusLabel = new JLabel(" ");
        ollamaModelStatusLabel.setFont(ollamaModelStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        ollamaFetchBtn.addActionListener(e -> fetchOllamaModels());
        fbOllama.addRowWithButton("Modellname:", ollamaModelCombo, ollamaFetchBtn);
        fbOllama.addWide(ollamaModelStatusLabel);
        ollamaKeepAliveField = new JTextField(20); fbOllama.addRow("Beibehalten für:", ollamaKeepAliveField);

        // Embeddings & Reranker models (same Ollama URL)
        fbOllama.addSection("Embeddings & Reranker");
        ollamaEmbeddingsModelCombo = new JComboBox<>();
        ollamaEmbeddingsModelCombo.setEditable(true);
        ollamaEmbeddingsModelCombo.setToolTipText("Ollama-Modell für /api/embeddings (RAG-Indexierung)");
        JLabel ollamaEmbLbl = new JLabel("Embeddings-Modell:");
        fbOllama.addRowWithButton(ollamaEmbLbl, ollamaEmbeddingsModelCombo,
                makeModelFetchButton("Verfügbare Modelle vom Ollama-Server abrufen", this::fetchOllamaModels));
        embedDependentFields.add(ollamaEmbLbl);
        embedDependentFields.add(ollamaEmbeddingsModelCombo);

        ollamaRerankModelCombo = new JComboBox<>();
        ollamaRerankModelCombo.setEditable(true);
        ollamaRerankModelCombo.setToolTipText("Ollama-Modell für Reranker (Cross-Encoder)");
        JLabel ollamaRerankLbl = new JLabel("Reranker-Modell:");
        fbOllama.addRowWithButton(ollamaRerankLbl, ollamaRerankModelCombo,
                makeModelFetchButton("Verfügbare Modelle vom Ollama-Server abrufen", this::fetchOllamaModels));
        rerankDependentFields.add(ollamaRerankLbl);
        rerankDependentFields.add(ollamaRerankModelCombo);

        fbOllama.addSection("Audio");
        JLabel ollamaAudioInfo = unsupportedInfoLabel("Ollama bietet aktuell keinen nativen TTS-Endpunkt.");
        fbOllama.addWide(ollamaAudioInfo);
        audioDependentFields.add(ollamaAudioInfo);

        fbOllama.addSection("Responses-API");
        JLabel ollamaResponsesInfo = unsupportedInfoLabel(
                "Ollama hat keine zustandsbehaftete Responses-API. Konversations-State müsste clientseitig verwaltet werden.");
        fbOllama.addWide(ollamaResponsesInfo);
        responsesDependentFields.add(ollamaResponsesInfo);

        // Proxy-Auth + E2E-Credentials sind in den globalen Proxy-Tab verschoben.
        // Die per-Tab Aktivierungs-Checkboxen liegen oben in der Toggle-Leiste (useProxyAuthBox / useE2eBox).
        // Die „Proxy-Scripte & Dokumentation…"-Schaltfläche ist ebenfalls in den Proxy-Tab umgezogen.
        fbOllama.addSeparator();
        JLabel ollamaProxyInfo = new JLabel(
                "<html><i>Proxy-Authentifizierung, E2E-Verschlüsselung und die Proxy-Scripte/Dokumentation "
                        + "werden global im <b>Proxy</b>-Tab konfiguriert. Die Aktivierung erfolgt pro KI-Tab "
                        + "über die Checkboxen \"Proxy-Auth\" und \"E2E\" oben.</i></html>");
        fbOllama.addWide(ollamaProxyInfo);


        // Sonstige Endpunkt-Pfade — Override für die Modell-Liste.
        fbOllama.addSection("Sonstige Endpunkt-Pfade");
        ollamaModelsEndpointField = new JTextField(
                settings.aiConfig.getOrDefault("ollama.endpoint.models", "/api/tags"), 30);
        ollamaModelsEndpointField.setToolTipText("Pfad zum Modell-Listen-Endpunkt (relativ zur Ollama-Base-URL). "
                + "Standard /api/tags; nur ändern, wenn ein Fork einen abweichenden Pfad anbietet.");
        JButton ollamaModelsEpReset = squareResetButton(() -> ollamaModelsEndpointField.setText("/api/tags"));
        fbOllama.addRowWithButton("GET Models:", ollamaModelsEndpointField, ollamaModelsEpReset);

        // Verbindungstest (Chat / Embeddings / Reranker)
        fbOllama.addSection("Verbindungstest");
        fbOllama.addWide(makeProviderTestRow(AiProvider.OLLAMA, this::snapshotOllama,
                de.bund.zrb.ui.settings.provider.Facet.CHAT,
                de.bund.zrb.ui.settings.provider.Facet.EMBEDDINGS,
                de.bund.zrb.ui.settings.provider.Facet.RERANKER));

        providerOptionsPanel.add(fbOllama.getPanel(), AiProvider.OLLAMA.name());

        // CLOUD
        FormBuilder fbCloud = new FormBuilder();
        cloudProviderField = new JComboBox<>(new String[]{"OPENAI","CLAUDE","PERPLEXITY","GROK","GEMINI"});
        fbCloud.addRow("Cloud-Anbieter:", cloudProviderField);
        cloudApiKeyField = new JTextField(30); fbCloud.addRow("API Key:", cloudApiKeyField);
        cloudApiUrlField = new JTextField(30); fbCloud.addRow("API URL:", cloudApiUrlField);
        cloudModelCombo = new JComboBox<>();
        cloudModelCombo.setEditable(true);
        JButton cloudFetchBtn = new JButton("🔄");
        cloudFetchBtn.setToolTipText("Verfügbare Modelle vom Anbieter abrufen");
        cloudFetchBtn.setMargin(new Insets(2, 4, 2, 4));
        cloudModelStatusLabel = new JLabel(" ");
        cloudModelStatusLabel.setFont(cloudModelStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        cloudFetchBtn.addActionListener(e -> fetchCloudModels());
        fbCloud.addRowWithButton("Modell:", cloudModelCombo, cloudFetchBtn);
        fbCloud.addWide(cloudModelStatusLabel);
        cloudAuthHeaderField = new JTextField(30); fbCloud.addRow("Auth Header:", cloudAuthHeaderField);
        cloudAuthPrefixField = new JTextField(30); fbCloud.addRow("Auth Prefix:", cloudAuthPrefixField);
        cloudApiVersionField = new JTextField(30); fbCloud.addRow("Anthropic-Version:", cloudApiVersionField);
        cloudOrgField = new JTextField(30); fbCloud.addRow("Organisation:", cloudOrgField);
        cloudProjectField = new JTextField(30); fbCloud.addRow("Projekt:", cloudProjectField);
        JButton cloudResetButton = new JButton("Defaults zurücksetzen");
        fbCloud.addButtons(cloudResetButton);

        // Embeddings & Reranker (own URL + model — independent of chat completions)
        fbCloud.addSection("Embeddings & Reranker");
        cloudEmbeddingsUrlField = new JTextField(30);
        cloudEmbeddingsUrlField.setToolTipText("Vollständige URL des Embeddings-Endpunkts, z. B. https://api.openai.com/v1/embeddings");
        JLabel cloudEmbUrlLbl = new JLabel("Embeddings URL:");
        fbCloud.addRow(cloudEmbUrlLbl, cloudEmbeddingsUrlField);
        cloudEmbeddingsModelCombo = new JComboBox<>();
        cloudEmbeddingsModelCombo.setEditable(true);
        cloudEmbeddingsModelCombo.setToolTipText("Modell für /v1/embeddings (RAG-Indexierung)");
        JLabel cloudEmbModelLbl = new JLabel("Embeddings-Modell:");
        fbCloud.addRowWithButton(cloudEmbModelLbl, cloudEmbeddingsModelCombo,
                makeModelFetchButton("Verfügbare Modelle vom Anbieter abrufen", this::fetchCloudModels));
        embedDependentFields.add(cloudEmbUrlLbl);
        embedDependentFields.add(cloudEmbeddingsUrlField);
        embedDependentFields.add(cloudEmbModelLbl);
        embedDependentFields.add(cloudEmbeddingsModelCombo);

        cloudRerankUrlField = new JTextField(30);
        cloudRerankUrlField.setToolTipText("Vollständige URL des Reranker-Endpunkts (z. B. https://api.jina.ai/v1/rerank)");
        JLabel cloudRerankUrlLbl = new JLabel("Reranker URL:");
        fbCloud.addRow(cloudRerankUrlLbl, cloudRerankUrlField);
        cloudRerankModelCombo = new JComboBox<>();
        cloudRerankModelCombo.setEditable(true);
        cloudRerankModelCombo.setToolTipText("Modell für Reranking (Cross-Encoder)");
        JLabel cloudRerankModelLbl = new JLabel("Reranker-Modell:");
        fbCloud.addRowWithButton(cloudRerankModelLbl, cloudRerankModelCombo,
                makeModelFetchButton("Verfügbare Modelle vom Anbieter abrufen", this::fetchCloudModels));
        rerankDependentFields.add(cloudRerankUrlLbl);
        rerankDependentFields.add(cloudRerankUrlField);
        rerankDependentFields.add(cloudRerankModelLbl);
        rerankDependentFields.add(cloudRerankModelCombo);

        // Audio (TTS) — von OpenAI/LocalAI unterstützt, von Claude/Perplexity/Grok i. d. R. nicht.
        fbCloud.addSection("Audio");
        cloudAudioUrlField = new JTextField(30);
        cloudAudioUrlField.setToolTipText("Vollständige URL des Audio/TTS-Endpunkts, z. B. https://api.openai.com/v1/audio/speech");
        JLabel cloudAudioUrlLbl = new JLabel("Audio URL:");
        fbCloud.addRow(cloudAudioUrlLbl, cloudAudioUrlField);
        cloudAudioModelCombo = new JComboBox<>();
        cloudAudioModelCombo.setEditable(true);
        cloudAudioModelCombo.setToolTipText("TTS-Modell, z. B. tts-1, tts-1-hd");
        JLabel cloudAudioModelLbl = new JLabel("Audio-Modell:");
        fbCloud.addRowWithButton(cloudAudioModelLbl, cloudAudioModelCombo,
                makeModelFetchButton("Verfügbare Modelle vom Anbieter abrufen", this::fetchCloudModels));
        JLabel cloudAudioHint = unsupportedInfoLabel(
                "Nicht jeder Cloud-Anbieter bietet einen TTS-Endpunkt — siehe Anbieter-Dokumentation.");
        fbCloud.addWide(cloudAudioHint);
        audioDependentFields.add(cloudAudioUrlLbl);
        audioDependentFields.add(cloudAudioUrlField);
        audioDependentFields.add(cloudAudioModelLbl);
        audioDependentFields.add(cloudAudioModelCombo);
        audioDependentFields.add(cloudAudioHint);

        // Responses-API — zustandsbehafteter Endpunkt (Chatnachrichten als Ressourcen).
        // Aktuell vor allem von OpenAI angeboten.
        fbCloud.addSection("Responses-API");
        JLabel cloudResponsesInfo = unsupportedInfoLabel(
                "Zustandsbehaftete API: Chatnachrichten werden serverseitig als Ressourcen verwaltet. "
                + "Nur von wenigen Anbietern unterstützt (z. B. OpenAI).");
        fbCloud.addWide(cloudResponsesInfo);
        cloudResponsesUrlField = new JTextField(30);
        cloudResponsesUrlField.setToolTipText("URL für POST /v1/responses");
        JLabel cloudResponsesUrlLbl = new JLabel("Responses URL:");
        fbCloud.addRow(cloudResponsesUrlLbl, cloudResponsesUrlField);
        cloudResponsesByIdUrlField = new JTextField(30);
        cloudResponsesByIdUrlField.setToolTipText("URL-Vorlage für GET /v1/responses/{response_id}");
        JLabel cloudResponsesByIdUrlLbl = new JLabel("Response-by-ID URL:");
        fbCloud.addRow(cloudResponsesByIdUrlLbl, cloudResponsesByIdUrlField);
        responsesDependentFields.add(cloudResponsesInfo);
        responsesDependentFields.add(cloudResponsesUrlLbl);
        responsesDependentFields.add(cloudResponsesUrlField);
        responsesDependentFields.add(cloudResponsesByIdUrlLbl);
        responsesDependentFields.add(cloudResponsesByIdUrlField);

        // Sonstige Endpunkt-Pfade — optionales Override für die Modell-Liste.
        // Leer lassen ⇒ Fetcher leitet automatisch aus 'API URL' ab (OPENAI: …/v1/models).
        fbCloud.addSection("Sonstige Endpunkt-Pfade");
        cloudModelsUrlField = new JTextField(30);
        cloudModelsUrlField.setToolTipText("Optional: vollständige URL des Modell-Listen-Endpunkts. "
                + "Leer lassen ⇒ wird aus 'API URL' abgeleitet. CLAUDE/PERPLEXITY bieten keinen solchen Endpunkt.");
        fbCloud.addRow("GET Models URL:", cloudModelsUrlField);

        // Verbindungstest (Chat / Embeddings / Reranker)
        fbCloud.addSection("Verbindungstest");
        fbCloud.addWide(makeProviderTestRow(AiProvider.CLOUD, this::snapshotCloud,
                de.bund.zrb.ui.settings.provider.Facet.CHAT,
                de.bund.zrb.ui.settings.provider.Facet.EMBEDDINGS,
                de.bund.zrb.ui.settings.provider.Facet.RERANKER));

        providerOptionsPanel.add(fbCloud.getPanel(), AiProvider.CLOUD.name());

        // PRIVATE_CLOUD: CardLayout with two sub-modes (OpenAI Compatible / Custom),
        // analogous to Public Cloud's vendor dropdown. The "Modus:" row sits inside each
        // sub-card's FormBuilder so its label aligns with the rest. A shared ComboBoxModel
        // keeps both combos synchronized.
        final JPanel privateSubCards = new JPanel(new CardLayout());
        DefaultComboBoxModel<String> privateModeModel = new DefaultComboBoxModel<>(
                new String[]{"OpenAI Compatible", "Custom"});
        privateModeCombo = new JComboBox<>(privateModeModel);
        privateModeComboCustom = new JComboBox<>(privateModeModel);

        // Inner sub-panels for the two modes (compatible / custom)
        // ── OpenAI Compatible sub-card ──
        FormBuilder fbOaic = new FormBuilder();
        fbOaic.addRow("Modus:", privateModeCombo);
        fbOaic.addInfo("<html>Selbstgehosteter Endpunkt mit OpenAI-Standardpfaden. "
                + "Die Defaults sind direkt editierbar; der ↺-Button setzt das Feld zurück.</html>");
        oaicBaseUrlField = new JTextField(30);
        oaicBaseUrlField.setToolTipText("Basis-URL ohne Pfad, z. B. https://api.openai.com — die Endpunkte werden daran angehängt.");
        JButton oaicBaseResetBtn = squareResetButton(() -> oaicBaseUrlField.setText("https://api.openai.com"));
        fbOaic.addRowWithButton("Base URL:", oaicBaseUrlField, oaicBaseResetBtn);
        oaicApiKeyField = new JTextField(30);
        fbOaic.addRow("API Key:", oaicApiKeyField);
        oaicModelCombo = new JComboBox<>();
        oaicModelCombo.setEditable(true);
        JButton oaicFetchBtn = new JButton("🔄");
        oaicFetchBtn.setToolTipText("Verfügbare Modelle abrufen (GET <Base URL><Models-Endpunkt>) und alle drei Modell-Dropdowns befüllen");
        oaicFetchBtn.setMargin(new Insets(2, 4, 2, 4));
        oaicModelStatusLabel = new JLabel(" ");
        oaicModelStatusLabel.setFont(oaicModelStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        oaicFetchBtn.addActionListener(e -> fetchOaicModels());
        fbOaic.addRowWithButton("Chat-Modell:", oaicModelCombo, oaicFetchBtn);
        fbOaic.addWide(oaicModelStatusLabel);
        fbOaic.addSection("Embeddings & Reranker");
        oaicEmbeddingsModelCombo = new JComboBox<>();
        oaicEmbeddingsModelCombo.setEditable(true);
        oaicEmbeddingsModelCombo.setToolTipText("Modell für /v1/embeddings (RAG-Indexierung)");
        JLabel oaicEmbLbl = new JLabel("Embeddings-Modell:");
        fbOaic.addRowWithButton(oaicEmbLbl, oaicEmbeddingsModelCombo,
                makeModelFetchButton("Verfügbare Modelle abrufen", this::fetchOaicModels));
        embedDependentFields.add(oaicEmbLbl);
        embedDependentFields.add(oaicEmbeddingsModelCombo);
        oaicRerankModelCombo = new JComboBox<>();
        oaicRerankModelCombo.setEditable(true);
        oaicRerankModelCombo.setToolTipText("Modell für /v1/rerank (RAG-Reranking)");
        JLabel oaicRerankLbl = new JLabel("Reranker-Modell:");
        fbOaic.addRowWithButton(oaicRerankLbl, oaicRerankModelCombo,
                makeModelFetchButton("Verfügbare Modelle abrufen", this::fetchOaicModels));
        rerankDependentFields.add(oaicRerankLbl);
        rerankDependentFields.add(oaicRerankModelCombo);
        // Embeddings/Rerank endpoint paths live within this same section for full consistency.
        JTextField oaicEmbPathField = makeEndpointField("embeddings");
        oaicEndpointFields.put("embeddings", oaicEmbPathField);
        fbOaic.addRowWithButton("POST Embeddings:", oaicEmbPathField,
                squareResetButton(() -> oaicEmbPathField.setText(defaultEndpoint("embeddings"))));
        embedDependentFields.add(oaicEmbPathField);
        JTextField oaicRerankPathField = makeEndpointField("rerank");
        oaicEndpointFields.put("rerank", oaicRerankPathField);
        fbOaic.addRowWithButton("POST Rerank:", oaicRerankPathField,
                squareResetButton(() -> oaicRerankPathField.setText(defaultEndpoint("rerank"))));
        rerankDependentFields.add(oaicRerankPathField);

        fbOaic.addSection("Audio");
        oaicAudioModelCombo = new JComboBox<>();
        oaicAudioModelCombo.setEditable(true);
        oaicAudioModelCombo.setToolTipText("TTS-Modell, z. B. tts-1");
        JLabel oaicAudioLbl = new JLabel("Audio-Modell:");
        fbOaic.addRowWithButton(oaicAudioLbl, oaicAudioModelCombo,
                makeModelFetchButton("Verfügbare Modelle abrufen", this::fetchOaicModels));
        JTextField oaicAudioPathField = makeEndpointField("audio");
        oaicEndpointFields.put("audio", oaicAudioPathField);
        fbOaic.addRowWithButton("POST Audio/Speech:", oaicAudioPathField,
                squareResetButton(() -> oaicAudioPathField.setText(defaultEndpoint("audio"))));
        audioDependentFields.add(oaicAudioLbl);
        audioDependentFields.add(oaicAudioModelCombo);
        audioDependentFields.add(oaicAudioPathField);

        fbOaic.addSection("Responses-API");
        JLabel oaicResponsesInfo = unsupportedInfoLabel(
                "Zustandsbehaftete API: Chatnachrichten werden serverseitig als Ressourcen verwaltet. "
                + "Wird nur unterstützt, wenn der Backend-Server diese Endpunkte bereitstellt.");
        fbOaic.addWide(oaicResponsesInfo);
        JTextField oaicResponsesField = makeEndpointField("responses");
        oaicEndpointFields.put("responses", oaicResponsesField);
        fbOaic.addRowWithButton("POST Responses:", oaicResponsesField,
                squareResetButton(() -> oaicResponsesField.setText(defaultEndpoint("responses"))));
        JTextField oaicResponseByIdField = makeEndpointField("responseById");
        oaicEndpointFields.put("responseById", oaicResponseByIdField);
        fbOaic.addRowWithButton("GET Response by ID:", oaicResponseByIdField,
                squareResetButton(() -> oaicResponseByIdField.setText(defaultEndpoint("responseById"))));
        responsesDependentFields.add(oaicResponsesInfo);
        responsesDependentFields.add(oaicResponsesField);
        responsesDependentFields.add(oaicResponseByIdField);

        fbOaic.addSection("Sonstige Endpunkt-Pfade (relativ zur Base URL)");
        JTextField oaicModelsField = makeEndpointField("models");
        oaicEndpointFields.put("models", oaicModelsField);
        fbOaic.addRowWithButton("GET Models:", oaicModelsField,
                squareResetButton(() -> oaicModelsField.setText(defaultEndpoint("models"))));
        JTextField oaicChatField = makeEndpointField("chat");
        oaicEndpointFields.put("chat", oaicChatField);
        fbOaic.addRowWithButton("POST Chat Completions:", oaicChatField,
                squareResetButton(() -> oaicChatField.setText(defaultEndpoint("chat"))));

        // ── Custom sub-card ──
        FormBuilder fbCustom = new FormBuilder();
        fbCustom.addRow("Modus:", privateModeComboCustom);
        fbCustom.addInfo("<html>Voll konfigurierbarer Endpunkt: alle Pfade sowie sämtliche HTTP-Header "
                + "werden manuell eingegeben. Endpunkt-Defaults entsprechen dem OpenAI-Schema und können per ↺ "
                + "wiederhergestellt werden.</html>");
        customBaseUrlField = new JTextField(30);
        customBaseUrlField.setToolTipText("Basis-URL ohne Pfad, z. B. https://api.example.com");
        JButton customBaseResetBtn = squareResetButton(() -> customBaseUrlField.setText(""));
        fbCustom.addRowWithButton("Base URL:", customBaseUrlField, customBaseResetBtn);
        customModelCombo = new JComboBox<>();
        customModelCombo.setEditable(true);
        JButton customFetchBtn = new JButton("🔄");
        customFetchBtn.setToolTipText("Verfügbare Modelle abrufen (GET <Base URL><Models-Endpunkt>) und alle drei Modell-Dropdowns befüllen");
        customFetchBtn.setMargin(new Insets(2, 4, 2, 4));
        customModelStatusLabel = new JLabel(" ");
        customModelStatusLabel.setFont(customModelStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        customFetchBtn.addActionListener(e -> fetchCustomModels());
        fbCustom.addRowWithButton("Chat-Modell:", customModelCombo, customFetchBtn);
        fbCustom.addWide(customModelStatusLabel);
        fbCustom.addSection("Embeddings & Reranker");
        customEmbeddingsModelCombo = new JComboBox<>();
        customEmbeddingsModelCombo.setEditable(true);
        customEmbeddingsModelCombo.setToolTipText("Modell für /v1/embeddings (RAG-Indexierung)");
        JLabel customEmbLbl = new JLabel("Embeddings-Modell:");
        fbCustom.addRowWithButton(customEmbLbl, customEmbeddingsModelCombo,
                makeModelFetchButton("Verfügbare Modelle abrufen", this::fetchCustomModels));
        embedDependentFields.add(customEmbLbl);
        embedDependentFields.add(customEmbeddingsModelCombo);
        // Embeddings-Pfad wird in der "Endpunkt-Pfade"-Sektion via OAI_ENDPOINT_DEFAULTS-Loop ergänzt.
        customRerankModelCombo = new JComboBox<>();
        customRerankModelCombo.setEditable(true);
        customRerankModelCombo.setToolTipText("Modell für /v1/rerank (RAG-Reranking)");
        JLabel customRerankLbl = new JLabel("Reranker-Modell:");
        fbCustom.addRowWithButton(customRerankLbl, customRerankModelCombo,
                makeModelFetchButton("Verfügbare Modelle abrufen", this::fetchCustomModels));
        rerankDependentFields.add(customRerankLbl);
        rerankDependentFields.add(customRerankModelCombo);

        fbCustom.addSection("Audio");
        customAudioModelCombo = new JComboBox<>();
        customAudioModelCombo.setEditable(true);
        customAudioModelCombo.setToolTipText("TTS-Modell, z. B. tts-1");
        JLabel customAudioLbl = new JLabel("Audio-Modell:");
        fbCustom.addRowWithButton(customAudioLbl, customAudioModelCombo,
                makeModelFetchButton("Verfügbare Modelle abrufen", this::fetchCustomModels));
        audioDependentFields.add(customAudioLbl);
        audioDependentFields.add(customAudioModelCombo);
        // Audio-Pfad wird ebenfalls in der "Endpunkt-Pfade"-Sektion verwaltet (key "audio").

        fbCustom.addSection("Responses-API");
        JLabel customResponsesInfo = unsupportedInfoLabel(
                "Zustandsbehaftete API: Chatnachrichten werden serverseitig als Ressourcen verwaltet. "
                + "Wird nur unterstützt, wenn der Backend-Server diese Endpunkte bereitstellt. "
                + "Die zugehörigen Pfade (POST Responses / GET Response by ID) findest du unten in den Endpunkt-Pfaden.");
        fbCustom.addWide(customResponsesInfo);
        responsesDependentFields.add(customResponsesInfo);

        fbCustom.addSection("HTTP-Header");
        customHeadersModel = new javax.swing.table.DefaultTableModel(new Object[]{"Header", "Wert"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return true; }
        };
        customHeadersTable = new JTable(customHeadersModel);
        customHeadersTable.setRowHeight(22);
        customHeadersTable.setFillsViewportHeight(true);
        customHeadersTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JScrollPane headersScroll = new JScrollPane(customHeadersTable);
        headersScroll.setPreferredSize(new Dimension(500, 120));
        fbCustom.addWide(headersScroll);
        JButton addHeaderBtn = new JButton("➕ Header");
        addHeaderBtn.addActionListener(e -> {
            if (customHeadersTable.isEditing()) customHeadersTable.getCellEditor().stopCellEditing();
            customHeadersModel.addRow(new Object[]{"", ""});
            int last = customHeadersModel.getRowCount() - 1;
            customHeadersTable.setRowSelectionInterval(last, last);
            customHeadersTable.editCellAt(last, 0);
            Component editor = customHeadersTable.getEditorComponent();
            if (editor != null) editor.requestFocusInWindow();
        });
        JButton removeHeaderBtn = new JButton("➖ Header");
        removeHeaderBtn.addActionListener(e -> {
            if (customHeadersTable.isEditing()) customHeadersTable.getCellEditor().stopCellEditing();
            int[] rows = customHeadersTable.getSelectedRows();
            for (int i = rows.length - 1; i >= 0; i--) customHeadersModel.removeRow(rows[i]);
        });
        JButton resetHeadersBtn = new JButton("↺ Defaults");
        resetHeadersBtn.setToolTipText("Header auf Standard zurücksetzen (Authorization: Bearer YOUR_API_KEY)");
        resetHeadersBtn.addActionListener(e -> {
            if (customHeadersTable.isEditing()) customHeadersTable.getCellEditor().stopCellEditing();
            customHeadersModel.setRowCount(0);
            customHeadersModel.addRow(new Object[]{"Authorization", "Bearer YOUR_API_KEY"});
            customHeadersModel.addRow(new Object[]{"Content-Type", "application/json"});
        });
        fbCustom.addButtons(addHeaderBtn, removeHeaderBtn, resetHeadersBtn);

        fbCustom.addSection("Endpunkt-Pfade (relativ zur Base URL)");
        for (String[] ep : OAI_ENDPOINT_DEFAULTS) {
            final String defaultPath = ep[1];
            final JTextField field = new JTextField(30);
            JButton resetBtn = squareResetButton(() -> field.setText(defaultPath));
            fbCustom.addRowWithButton(ep[2] + ":", field, resetBtn);
            customEndpointFields.put(ep[0], field);
        }

        // Verbindungstest pro Sub-Modus
        fbOaic.addSection("Verbindungstest");
        fbOaic.addWide(makeProviderTestRow(AiProvider.PRIVATE_CLOUD,
                () -> {
                    java.util.Map<String, String> s = snapshotOaic();
                    s.put("privateCloud.mode", "compatible");
                    return s;
                },
                de.bund.zrb.ui.settings.provider.Facet.CHAT,
                de.bund.zrb.ui.settings.provider.Facet.EMBEDDINGS,
                de.bund.zrb.ui.settings.provider.Facet.RERANKER));

        fbCustom.addSection("Verbindungstest");
        fbCustom.addWide(makeProviderTestRow(AiProvider.PRIVATE_CLOUD,
                () -> {
                    java.util.Map<String, String> s = snapshotCustom();
                    s.put("privateCloud.mode", "custom");
                    return s;
                },
                de.bund.zrb.ui.settings.provider.Facet.CHAT,
                de.bund.zrb.ui.settings.provider.Facet.EMBEDDINGS,
                de.bund.zrb.ui.settings.provider.Facet.RERANKER));

        // ── Assemble Private Cloud card (CardLayout switches sub-modes via shared ComboBoxModel) ──
        privateSubCards.add(fbOaic.getPanel(), "compatible");
        privateSubCards.add(fbCustom.getPanel(), "custom");
        ActionListener privateModeSwitch = e -> {
            String key = "OpenAI Compatible".equals(privateModeModel.getSelectedItem()) ? "compatible" : "custom";
            ((CardLayout) privateSubCards.getLayout()).show(privateSubCards, key);
        };
        privateModeCombo.addActionListener(privateModeSwitch);
        privateModeComboCustom.addActionListener(privateModeSwitch);
        providerOptionsPanel.add(privateSubCards, AiProvider.PRIVATE_CLOUD.name());

        // LOCAL_AI — datengetrieben gerendert.
        localAiCard = ProviderCardRenderer.render(
                ProviderDefinitions.localAi(), EnumSet.allOf(Facet.class),
                () -> useProxyBox.isSelected());
        localAiBaseUrlField        = (JTextField) localAiCard.getComponent("localai.url");
        localAiModelField          = (JTextField) localAiCard.getComponent("localai.model");
        localAiEmbeddingsUrlField  = (JTextField) localAiCard.getComponent("localai.url.embeddings");
        localAiEmbeddingsModelField= (JTextField) localAiCard.getComponent("localai.model.embeddings");
        localAiRerankUrlField      = (JTextField) localAiCard.getComponent("localai.url.rerank");
        localAiRerankModelField    = (JTextField) localAiCard.getComponent("localai.model.rerank");
        localAiAudioUrlField       = (JTextField) localAiCard.getComponent("localai.url.audio");
        localAiAudioModelField     = (JTextField) localAiCard.getComponent("localai.model.audio");
        embedDependentFields.addAll(localAiCard.getFacetComponents(Facet.EMBEDDINGS));
        rerankDependentFields.addAll(localAiCard.getFacetComponents(Facet.RERANKER));
        audioDependentFields.addAll(localAiCard.getFacetComponents(Facet.AUDIO));
        responsesDependentFields.addAll(localAiCard.getFacetComponents(Facet.RESPONSES));

        providerOptionsPanel.add(localAiCard.getPanel(), AiProvider.LOCAL_AI.name());

        // LLAMA
        FormBuilder fbLlama = new FormBuilder();
        llamaStreamingBox = new JCheckBox("Streaming aktiviert");
        llamaStreamingBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.streaming", "true")));
        fbLlama.addWide(llamaStreamingBox);
        llamaEnabledBox = new JCheckBox("llama.cpp Server beim Start starten");
        llamaEnabledBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.enabled", "false")));
        fbLlama.addWide(llamaEnabledBox);
        llamaBinaryField = new JTextField(settings.aiConfig.getOrDefault("llama.binary", "C:/llamacpp/llama-server"), 30);
        JButton extractBtn = new JButton("🔄 Entpacken");
        extractBtn.addActionListener(e -> {
            String path = llamaBinaryField.getText().trim();
            if (path.isEmpty()) { JOptionPane.showMessageDialog(null, "Bitte Zielpfad angeben.", "Pfad fehlt", JOptionPane.WARNING_MESSAGE); return; }
            String inputHash = (String) JOptionPane.showInputDialog(null, "SHA-256-Hash:", "Hashprüfung", JOptionPane.PLAIN_MESSAGE, null, null, ExecutableLauncher.getHash());
            if (inputHash == null || inputHash.trim().isEmpty()) return;
            try { new ExecutableLauncher().extractTo(new File(path), inputHash.trim());
                JOptionPane.showMessageDialog(null, "Binary extrahiert:\n" + path, "Erfolg", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) { JOptionPane.showMessageDialog(null, "Fehler:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE); }
        });
        fbLlama.addRowWithButton("Binary-Pfad:", llamaBinaryField, extractBtn);
        llamaModelField = new JTextField(settings.aiConfig.getOrDefault("llama.model", "models/mistral.gguf"), 30);
        fbLlama.addRow("Modellpfad (.gguf):", llamaModelField);
        llamaPortSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.port", "8080")), 1024, 65535, 1));
        fbLlama.addRow("Port:", llamaPortSpinner);
        llamaThreadsSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.threads", "4")), 1, 64, 1));
        fbLlama.addRow("Threads:", llamaThreadsSpinner);
        llamaContextSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.context", "2048")), 512, 8192, 64));
        fbLlama.addRow("Kontextgröße:", llamaContextSpinner);
        llamaTempField = new JTextField(settings.aiConfig.getOrDefault("llama.temp", "0.7"), 5);
        fbLlama.addRow("Temperatur:", llamaTempField);

        // Embeddings & Reranker — llama.cpp Server bedient denselben Port; nur die Modelldateien
        // unterscheiden sich. Eine separate URL ist daher nicht erforderlich.
        fbLlama.addSection("Embeddings & Reranker");
        fbLlama.addInfo("<html><small>Nutzen denselben Server (Port s. o.) — separate Modelldateien.</small></html>");
        llamaEmbeddingsModelField = new JTextField(
                settings.aiConfig.getOrDefault("llama.model.embeddings", ""), 30);
        llamaEmbeddingsModelField.setToolTipText("Pfad zur Embedding-Modelldatei (.gguf), z. B. models/nomic-embed.gguf");
        JLabel llEmbLbl = new JLabel("Embeddings-Modellpfad:");
        fbLlama.addRow(llEmbLbl, llamaEmbeddingsModelField);
        embedDependentFields.add(llEmbLbl);
        embedDependentFields.add(llamaEmbeddingsModelField);

        llamaRerankModelField = new JTextField(
                settings.aiConfig.getOrDefault("llama.model.rerank", ""), 30);
        llamaRerankModelField.setToolTipText("Pfad zur Reranker-Modelldatei (.gguf)");
        JLabel llRerankLbl = new JLabel("Reranker-Modellpfad:");
        fbLlama.addRow(llRerankLbl, llamaRerankModelField);
        rerankDependentFields.add(llRerankLbl);
        rerankDependentFields.add(llamaRerankModelField);

        fbLlama.addSection("Audio");
        JLabel llamaAudioInfo = unsupportedInfoLabel(
                "llama.cpp Server unterstützt keinen nativen TTS-Endpunkt.");
        fbLlama.addWide(llamaAudioInfo);
        audioDependentFields.add(llamaAudioInfo);

        fbLlama.addSection("Responses-API");
        JLabel llamaResponsesInfo = unsupportedInfoLabel(
                "llama.cpp Server hat keine zustandsbehaftete Responses-API. "
                + "Konversations-State müsste clientseitig verwaltet werden.");
        fbLlama.addWide(llamaResponsesInfo);
        responsesDependentFields.add(llamaResponsesInfo);

        // Verbindungstest (Chat-Health / Embeddings / Reranker)
        fbLlama.addSection("Verbindungstest");
        fbLlama.addWide(makeProviderTestRow(AiProvider.LLAMA_CPP_SERVER, this::snapshotLlama,
                de.bund.zrb.ui.settings.provider.Facet.CHAT,
                de.bund.zrb.ui.settings.provider.Facet.EMBEDDINGS,
                de.bund.zrb.ui.settings.provider.Facet.RERANKER));

        providerOptionsPanel.add(fbLlama.getPanel(), AiProvider.LLAMA_CPP_SERVER.name());

        // ONNX Runtime
        FormBuilder fbOnnx = new FormBuilder();
        fbOnnx.addInfo("<html><b>ONNX Runtime</b> – lokale LLM-Inferenz mit Phi-3/Phi-4 Modellen.<br>"
                + "Modell als ONNX-Verzeichnis von Hugging Face herunterladen.</html>");
        String defaultPath = settings.aiConfig.getOrDefault("onnx.model.path", "");
        if (defaultPath.isEmpty() && ONNX_MANIFEST != null && ModelDownloader.isModelPresent(ONNX_MANIFEST)) {
            defaultPath = ModelDownloader.getModelDir(ONNX_MANIFEST).toAbsolutePath().toString();
        }
        onnxModelPathField = new JTextField(defaultPath, 30);
        onnxModelPathField.setToolTipText("Pfad zum ONNX-Modellverzeichnis (z.B. C:\\models\\Phi-3-mini-4k-instruct-onnx)");
        JButton onnxBrowseBtn = new JButton("\u2026");
        onnxBrowseBtn.setMargin(new Insets(0, 4, 0, 4));
        onnxBrowseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String current = onnxModelPathField.getText().trim();
            if (!current.isEmpty()) fc.setCurrentDirectory(new java.io.File(current));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                onnxModelPathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel onnxPathPanel = new JPanel(new BorderLayout(4, 0));
        onnxPathPanel.add(onnxModelPathField, BorderLayout.CENTER);
        JPanel onnxBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        onnxBtnPanel.add(onnxBrowseBtn);
        JButton onnxDownloadBtn = new JButton("\u2B07 Download");
        onnxDownloadBtn.setToolTipText("Phi-3 Mini ONNX-Modell (~2 GB) nach ~/.mainframemate/model/ herunterladen");
        if (ONNX_MANIFEST != null) {
            onnxDownloadBtn.addActionListener(e -> ModelDownloader.download(
                    this, ONNX_MANIFEST, path -> onnxModelPathField.setText(path)));
        } else {
            onnxDownloadBtn.setEnabled(false);
            onnxDownloadBtn.setToolTipText("model-manifest.json nicht gefunden – Download nicht verfügbar");
        }
        onnxBtnPanel.add(onnxDownloadBtn);
        onnxPathPanel.add(onnxBtnPanel, BorderLayout.EAST);

        fbOnnx.addRow("Modellpfad:", onnxPathPanel);
        onnxExecutionProviderCombo = new JComboBox<>(new String[]{"directml", "cpu"});
        onnxExecutionProviderCombo.setSelectedItem(
                settings.aiConfig.getOrDefault("onnx.execution.provider", "directml"));
        onnxExecutionProviderCombo.setToolTipText(
                "DirectML = GPU (empfohlen). CPU = Fallback ohne GPU-Beschleunigung.");
        fbOnnx.addRow("Execution Provider:", onnxExecutionProviderCombo);
        onnxMaxTokensSpinner = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(settings.aiConfig.getOrDefault("onnx.max.tokens", "256")), 1, 4096, 64));
        fbOnnx.addRow("Max Tokens:", onnxMaxTokensSpinner);
        onnxTemperatureField = new JTextField(settings.aiConfig.getOrDefault("onnx.temperature", "0.7"), 8);
        fbOnnx.addRow("Temperatur:", onnxTemperatureField);
        onnxTopPField = new JTextField(settings.aiConfig.getOrDefault("onnx.top.p", "0.9"), 8);
        fbOnnx.addRow("Top-P:", onnxTopPField);
        onnxTopKSpinner = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(settings.aiConfig.getOrDefault("onnx.top.k", "40")), 0, 1000, 1));
        fbOnnx.addRow("Top-K:", onnxTopKSpinner);

        // Embeddings & Reranker — eigene ONNX-Modellverzeichnisse pro Aufgabe.
        fbOnnx.addSection("Embeddings & Reranker");
        fbOnnx.addInfo("<html><small>Eigene ONNX-Modellverzeichnisse für Embeddings/Reranker.</small></html>");
        onnxEmbeddingsModelPathField = new JTextField(
                settings.aiConfig.getOrDefault("onnx.model.embeddings.path", ""), 30);
        onnxEmbeddingsModelPathField.setToolTipText("Pfad zum ONNX-Embedding-Modellverzeichnis");
        JLabel onnxEmbLbl = new JLabel("Embeddings-Modellpfad:");
        fbOnnx.addRow(onnxEmbLbl, onnxEmbeddingsModelPathField);
        embedDependentFields.add(onnxEmbLbl);
        embedDependentFields.add(onnxEmbeddingsModelPathField);

        onnxRerankModelPathField = new JTextField(
                settings.aiConfig.getOrDefault("onnx.model.rerank.path", ""), 30);
        onnxRerankModelPathField.setToolTipText("Pfad zum ONNX-Reranker-Modellverzeichnis");
        JLabel onnxRerankLbl = new JLabel("Reranker-Modellpfad:");
        fbOnnx.addRow(onnxRerankLbl, onnxRerankModelPathField);
        rerankDependentFields.add(onnxRerankLbl);
        rerankDependentFields.add(onnxRerankModelPathField);

        fbOnnx.addSection("Audio");
        JLabel onnxAudioInfo = unsupportedInfoLabel(
                "ONNX Runtime führt aktuell nur LLM-Inferenz aus; ein nativer TTS-Endpunkt ist nicht vorgesehen.");
        fbOnnx.addWide(onnxAudioInfo);
        audioDependentFields.add(onnxAudioInfo);

        fbOnnx.addSection("Responses-API");
        JLabel onnxResponsesInfo = unsupportedInfoLabel(
                "Lokale ONNX-Inferenz ist zustandslos. Eine Responses-API (stateful) wird nicht bereitgestellt; "
                + "Konversations-State müsste clientseitig verwaltet werden.");
        fbOnnx.addWide(onnxResponsesInfo);
        responsesDependentFields.add(onnxResponsesInfo);

        providerOptionsPanel.add(fbOnnx.getPanel(), AiProvider.ONNX_RUNTIME.name());

        List<Component> llamaConfigFields = Arrays.asList(llamaBinaryField, llamaModelField, llamaPortSpinner, llamaThreadsSpinner, llamaContextSpinner, llamaTempField);
        llamaEnabledBox.addActionListener(e -> { boolean en = llamaEnabledBox.isSelected(); for (Component c : llamaConfigFields) c.setEnabled(en); });
        for (Component c : llamaConfigFields) c.setEnabled(llamaEnabledBox.isSelected());

        fb.addWide(providerOptionsPanel);

        // Set initial values
        String providerName = settings.aiConfig.getOrDefault("provider", "DISABLED");
        AiProvider selectedProvider;
        try { selectedProvider = AiProvider.valueOf(providerName); } catch (IllegalArgumentException ex) { selectedProvider = AiProvider.DISABLED; }
        providerCombo.setSelectedItem(selectedProvider);

        ollamaUrlField.setText(settings.aiConfig.getOrDefault("ollama.url", "http://localhost:11434/api/chat"));
        ollamaModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("ollama.model", "custom-modell"));
        ollamaKeepAliveField.setText(settings.aiConfig.getOrDefault("ollama.keepalive", "10m"));
        ollamaEmbeddingsModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("ollama.model.embeddings", ""));
        ollamaRerankModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("ollama.model.rerank", ""));
        if (ollamaModelsEndpointField != null) {
            ollamaModelsEndpointField.setText(settings.aiConfig.getOrDefault("ollama.endpoint.models", "/api/tags"));
        }

        String initialCloudVendor = settings.aiConfig.getOrDefault("cloud.vendor", "OPENAI");
        if ("CLOUD".equalsIgnoreCase(initialCloudVendor)) initialCloudVendor = "CLAUDE";
        cloudProviderField.setSelectedItem(initialCloudVendor);
        applyCloudVendorDefaults(false);
        cloudApiKeyField.setText(settings.aiConfig.getOrDefault("cloud.apikey", ""));
        cloudApiUrlField.setText(settings.aiConfig.getOrDefault("cloud.url", cloudDefaultForVendor(initialCloudVendor, "url")));
        cloudModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("cloud.model", cloudDefaultForVendor(initialCloudVendor, "model")));
        cloudAuthHeaderField.setText(settings.aiConfig.getOrDefault("cloud.authHeader", cloudDefaultForVendor(initialCloudVendor, "authHeader")));
        cloudAuthPrefixField.setText(settings.aiConfig.getOrDefault("cloud.authPrefix", cloudDefaultForVendor(initialCloudVendor, "authPrefix")));
        cloudOrgField.setText(settings.aiConfig.getOrDefault("cloud.organization", ""));
        cloudProjectField.setText(settings.aiConfig.getOrDefault("cloud.project", ""));
        cloudApiVersionField.setText(settings.aiConfig.getOrDefault("cloud.anthropicVersion", "2023-06-01"));
        cloudEmbeddingsUrlField.setText(settings.aiConfig.getOrDefault("cloud.url.embeddings", ""));
        cloudEmbeddingsModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("cloud.model.embeddings", ""));
        cloudRerankUrlField.setText(settings.aiConfig.getOrDefault("cloud.url.rerank", ""));
        cloudRerankModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("cloud.model.rerank", ""));
        cloudAudioUrlField.setText(settings.aiConfig.getOrDefault("cloud.url.audio", ""));
        cloudAudioModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("cloud.model.audio", ""));
        cloudResponsesUrlField.setText(settings.aiConfig.getOrDefault("cloud.url.responses", ""));
        cloudResponsesByIdUrlField.setText(settings.aiConfig.getOrDefault("cloud.url.responseById", ""));
        if (cloudModelsUrlField != null) {
            cloudModelsUrlField.setText(settings.aiConfig.getOrDefault("cloud.url.models", ""));
        }

        localAiBaseUrlField.setText(settings.aiConfig.getOrDefault("localai.url", ""));
        localAiModelField.setText(settings.aiConfig.getOrDefault("localai.model", ""));
        localAiEmbeddingsUrlField.setText(settings.aiConfig.getOrDefault("localai.url.embeddings", ""));
        localAiEmbeddingsModelField.setText(settings.aiConfig.getOrDefault("localai.model.embeddings", ""));
        localAiRerankUrlField.setText(settings.aiConfig.getOrDefault("localai.url.rerank", ""));
        localAiRerankModelField.setText(settings.aiConfig.getOrDefault("localai.model.rerank", ""));
        localAiAudioUrlField.setText(settings.aiConfig.getOrDefault("localai.url.audio", ""));
        localAiAudioModelField.setText(settings.aiConfig.getOrDefault("localai.model.audio", ""));

        // Private Cloud sub-mode dropdown
        String privateMode = settings.aiConfig.getOrDefault("privateCloud.mode", "compatible");
        privateModeCombo.setSelectedItem("custom".equalsIgnoreCase(privateMode) ? "Custom" : "OpenAI Compatible");

        // OpenAI Compatible initial values (with legacy migration from privateCloud.* when mode was "compatible")
        boolean legacyCompatible = "compatible".equalsIgnoreCase(
                settings.aiConfig.getOrDefault("privateCloud.mode", ""));
        oaicBaseUrlField.setText(settings.aiConfig.getOrDefault("openaiCompatible.baseUrl",
                legacyCompatible ? settings.aiConfig.getOrDefault("privateCloud.baseUrl", "") : ""));
        oaicApiKeyField.setText(settings.aiConfig.getOrDefault("openaiCompatible.apikey",
                legacyCompatible ? settings.aiConfig.getOrDefault("privateCloud.apikey", "") : ""));
        oaicModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("openaiCompatible.model",
                legacyCompatible ? settings.aiConfig.getOrDefault("privateCloud.model", "") : ""));
        oaicEmbeddingsModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("openaiCompatible.model.embeddings",
                legacyCompatible ? settings.aiConfig.getOrDefault("privateCloud.model.embeddings", "") : ""));
        oaicRerankModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("openaiCompatible.model.rerank",
                legacyCompatible ? settings.aiConfig.getOrDefault("privateCloud.model.rerank", "") : ""));
        oaicAudioModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("openaiCompatible.model.audio", ""));
        for (String[] ep : OAI_ENDPOINT_DEFAULTS) {
            JTextField field = oaicEndpointFields.get(ep[0]);
            String value = settings.aiConfig.get("openaiCompatible.endpoint." + ep[0]);
            if (value == null && legacyCompatible) value = settings.aiConfig.get("privateCloud.endpoint." + ep[0]);
            field.setText(value != null ? value : ep[1]);
        }

        // Custom initial values (with legacy migration from privateCloud.* when mode was "custom")
        boolean legacyCustom = "custom".equalsIgnoreCase(
                settings.aiConfig.getOrDefault("privateCloud.mode", ""));
        customBaseUrlField.setText(settings.aiConfig.getOrDefault("custom.baseUrl",
                legacyCustom ? settings.aiConfig.getOrDefault("privateCloud.baseUrl", "") : ""));
        customModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("custom.model",
                legacyCustom ? settings.aiConfig.getOrDefault("privateCloud.model", "") : ""));
        customEmbeddingsModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("custom.model.embeddings",
                legacyCustom ? settings.aiConfig.getOrDefault("privateCloud.model.embeddings", "") : ""));
        customRerankModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("custom.model.rerank",
                legacyCustom ? settings.aiConfig.getOrDefault("privateCloud.model.rerank", "") : ""));
        customAudioModelCombo.setSelectedItem(settings.aiConfig.getOrDefault("custom.model.audio", ""));
        for (String[] ep : OAI_ENDPOINT_DEFAULTS) {
            JTextField field = customEndpointFields.get(ep[0]);
            String value = settings.aiConfig.get("custom.endpoint." + ep[0]);
            if (value == null && legacyCustom) value = settings.aiConfig.get("privateCloud.endpoint." + ep[0]);
            field.setText(value != null ? value : ep[1]);
        }
        // Custom HTTP headers — load all custom.header.<name>=<value> entries; migrate legacy auth fields
        customHeadersModel.setRowCount(0);
        java.util.List<String> headerKeys = new java.util.ArrayList<>();
        for (String k : settings.aiConfig.keySet()) {
            if (k.startsWith("custom.header.")) headerKeys.add(k);
        }
        java.util.Collections.sort(headerKeys);
        for (String k : headerKeys) {
            String name = k.substring("custom.header.".length());
            customHeadersModel.addRow(new Object[]{name, settings.aiConfig.getOrDefault(k, "")});
        }
        if (customHeadersModel.getRowCount() == 0) {
            // Migrate legacy custom.authHeader/authPrefix/apikey/anthropicVersion into header rows
            String legacyHeader = settings.aiConfig.getOrDefault("custom.authHeader", "");
            String legacyPrefix = settings.aiConfig.getOrDefault("custom.authPrefix", "");
            String legacyKey = settings.aiConfig.getOrDefault("custom.apikey", "");
            String legacyAnthropic = settings.aiConfig.getOrDefault("custom.anthropicVersion", "");
            if (legacyCustom) {
                if (legacyHeader.isEmpty()) legacyHeader = settings.aiConfig.getOrDefault("privateCloud.authHeader", "");
                if (legacyPrefix.isEmpty()) legacyPrefix = settings.aiConfig.getOrDefault("privateCloud.authPrefix", "");
                if (legacyKey.isEmpty()) legacyKey = settings.aiConfig.getOrDefault("privateCloud.apikey", "");
                if (legacyAnthropic.isEmpty()) legacyAnthropic = settings.aiConfig.getOrDefault("privateCloud.anthropicVersion", "");
            }
            if (!legacyHeader.isEmpty() && !legacyKey.isEmpty()) {
                String val = legacyPrefix.isEmpty() ? legacyKey : (legacyPrefix + " " + legacyKey);
                customHeadersModel.addRow(new Object[]{legacyHeader, val});
            } else if (!legacyKey.isEmpty()) {
                customHeadersModel.addRow(new Object[]{"Authorization", "Bearer " + legacyKey});
            }
            if (!legacyAnthropic.isEmpty()) {
                customHeadersModel.addRow(new Object[]{"anthropic-version", legacyAnthropic});
            }
            if (customHeadersModel.getRowCount() == 0) {
                customHeadersModel.addRow(new Object[]{"Authorization", "Bearer YOUR_API_KEY"});
                customHeadersModel.addRow(new Object[]{"Content-Type", "application/json"});
            }
        }

        cloudProviderField.addActionListener(e -> applyCloudVendorDefaults(true));
        cloudResetButton.addActionListener(e -> {
            applyCloudVendorDefaults(true); cloudApiKeyField.setText("");
            if (!"OPENAI".equals(cloudProviderField.getSelectedItem())) { cloudOrgField.setText(""); cloudProjectField.setText(""); }
        });

        providerCombo.addActionListener(e -> ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, ((AiProvider) providerCombo.getSelectedItem()).name()));
        ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, selectedProvider.name());

        // ── Build tabbed UI: Allgemein (this provider config) + Embeddings (RAG) + Reranker ──
        embeddingsTab = new de.bund.zrb.ui.settings.RagSettingsPanel();
        rerankerTab = new RerankerSettingsPanel();
        summarizerTab = new SummarizerSettingsPanel();

        // Reparent the per-tab "aktiviert"-Checkboxes above the tab bar so a single control
        // drives both the persisted enabled-state (the inner panels keep saving from these
        // very instances) AND the UI grayout for embeddings/reranker rows across all
        // provider cards in the Allgemein-Tab.
        topEmbeddingsEnabledBox = embeddingsTab.getEnabledBox();
        topEmbeddingsEnabledBox.setText("Embeddings aktiviert");
        topRerankEnabledBox = rerankerTab.getEnabledBox();
        topRerankEnabledBox.setText("Reranker aktiviert");
        JCheckBox topSummarizerEnabledBox = summarizerTab.getEnabledBox();
        topSummarizerEnabledBox.setText("Summarizer aktiviert");

        // Audio / Responses toggles are panel-local (no nested tab) — persisted in aiConfig
        // and applied across all provider cards via the *DependentFields lists.
        topAudioEnabledBox = new JCheckBox("Audio aktiviert");
        topAudioEnabledBox.setSelected(false);
        topAudioEnabledBox.setEnabled(false);
        topAudioEnabledBox.setToolTipText("Wird derzeit noch nicht unterstützt.");
        topResponsesEnabledBox = new JCheckBox("Responses-API aktiviert");
        topResponsesEnabledBox.setSelected(false);
        topResponsesEnabledBox.setEnabled(false);
        topResponsesEnabledBox.setToolTipText(
                "Wird derzeit noch nicht unterstützt. Konversations-State wird aktuell clientseitig verwaltet; "
                        + "eine serverseitige Responses-API würde eine Überarbeitung des Chats erfordern.");

        JPanel topToggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        topToggles.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        topToggles.add(topEmbeddingsEnabledBox); // implicit reparent
        topToggles.add(topRerankEnabledBox);     // implicit reparent
        topToggles.add(topSummarizerEnabledBox); // implicit reparent
        topToggles.add(topAudioEnabledBox);
        topToggles.add(topResponsesEnabledBox);

        // Per-Tab Proxy-Schalter (Allgemein-Tab): steuert 🔄-Modellabruf und 🧪-Test
        if (useProxyBox == null) {
            useProxyBox = new JCheckBox("Proxy verwenden");
            useProxyBox.setToolTipText(
                    "Wenn aktiv, werden Modell-Abruf und Verbindungstest über den global "
                            + "konfigurierten Proxy geleitet. Aus = direkt (DIRECT).");
        }
        useProxyBox.setSelected(Boolean.parseBoolean(
                settings.aiConfig.getOrDefault("useProxy", "true")));
        topToggles.add(useProxyBox);
        // Migration: alte Ollama-Schalter aktivieren das neue Per-Tab-Flag, falls noch kein
        // explizites useProxyAuth/useE2e gespeichert wurde.
        boolean legacyProxyAuth = !settings.aiConfig.getOrDefault("ollama.proxy.username", "").isEmpty();
        boolean legacyE2e = !settings.aiConfig.getOrDefault("ollama.e2e.password", "").isEmpty();
        useProxyAuthBox.setSelected(Boolean.parseBoolean(
                settings.aiConfig.getOrDefault("useProxyAuth", String.valueOf(legacyProxyAuth))));
        useE2eBox.setSelected(Boolean.parseBoolean(
                settings.aiConfig.getOrDefault("useE2e", String.valueOf(legacyE2e))));
        topToggles.add(useProxyAuthBox);
        topToggles.add(useE2eBox);

        // Hook listeners that gray out the embeddings/reranker rows across every provider card.
        topEmbeddingsEnabledBox.addActionListener(e -> applyEmbeddingsEnabledState());
        topRerankEnabledBox.addActionListener(e -> applyRerankEnabledState());
        topAudioEnabledBox.addActionListener(e -> applyAudioEnabledState());
        topResponsesEnabledBox.addActionListener(e -> applyResponsesEnabledState());
        applyEmbeddingsEnabledState();
        applyRerankEnabledState();
        applyAudioEnabledState();
        applyResponsesEnabledState();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Allgemein", new JScrollPane(fb.getPanel()));
        tabs.addTab("Embeddings", embeddingsTab);
        tabs.addTab("Reranker", rerankerTab);
        tabs.addTab("Summarizer", summarizerTab);

        add(topToggles, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private void applyEmbeddingsEnabledState() {
        boolean on = topEmbeddingsEnabledBox != null && topEmbeddingsEnabledBox.isSelected();
        for (java.awt.Component c : embedDependentFields) c.setEnabled(on);
    }

    private void applyRerankEnabledState() {
        boolean on = topRerankEnabledBox != null && topRerankEnabledBox.isSelected();
        for (java.awt.Component c : rerankDependentFields) c.setEnabled(on);
    }

    private void applyAudioEnabledState() {
        boolean on = topAudioEnabledBox != null && topAudioEnabledBox.isSelected();
        for (java.awt.Component c : audioDependentFields) c.setEnabled(on);
    }

    private void applyResponsesEnabledState() {
        boolean on = topResponsesEnabledBox != null && topResponsesEnabledBox.isSelected();
        for (java.awt.Component c : responsesDependentFields) c.setEnabled(on);
    }

    /**
     * Override apply() to also persist the Embeddings (RAG) and Reranker tabs that now live
     * inside this category. Each inner panel writes its own disjoint subset of the Settings
     * (aiConfig vs embeddingConfig vs rerankerConfig), so a sequential save is safe.
     */
    @Override
    public void apply() {
        super.apply(); // writes aiConfig (this panel's applyToSettings)
        if (embeddingsTab != null) {
            Settings s = de.bund.zrb.helper.SettingsHelper.load();
            embeddingsTab.saveToSettings(s);
            de.bund.zrb.helper.SettingsHelper.save(s);
        }
        if (rerankerTab != null) {
            rerankerTab.apply(); // SettingsCategory.apply() handles load+save+afterApply
        }
        if (summarizerTab != null) {
            summarizerTab.apply(); // persists summarizerConfig + clears summarizer cache via afterApply
        }

        // Live-refresh the RagService clients with the freshly persisted settings —
        // covers both the "overwrite=true" path (changes in Embeddings/Reranker tabs) AND
        // the "overwrite=false" path (changes in the Allgemein tab that flow through aiConfig).
        // Guarded by isInitialized() so we don't force a cold start of the RAG pipeline
        // (Lucene index rebuild, …) just because the user opened the AI settings.
        if (de.bund.zrb.rag.service.RagService.isInitialized()) {
            try {
                de.bund.zrb.rag.service.RagService rag = de.bund.zrb.rag.service.RagService.getInstance();
                rag.updateEmbeddingSettings(
                        de.bund.zrb.rag.config.EmbeddingSettings.fromStoredConfig());
                rag.updateRerankerSettings(
                        de.bund.zrb.rag.config.RerankerSettings.fromStoredConfig());
            } catch (Exception ignored) {
                // RagService refresh failures must not break the settings-save flow.
            }
        }
    }

    @Override
    protected void applyToSettings(Settings s) {
        AiProvider activeProvider = (AiProvider) providerCombo.getSelectedItem();
        s.aiConfig.put("provider", activeProvider != null ? activeProvider.name() : AiProvider.DISABLED.name());
        s.aiConfig.put("useProxy", String.valueOf(useProxyBox.isSelected()));
        s.aiConfig.put("useProxyAuth", String.valueOf(useProxyAuthBox.isSelected()));
        s.aiConfig.put("useE2e", String.valueOf(useE2eBox.isSelected()));
        s.aiConfig.put("ollama.url", ollamaUrlField.getText().trim());
        s.aiConfig.put("ollama.model", Objects.toString(ollamaModelCombo.getSelectedItem(), "").trim());
        s.aiConfig.put("ollama.keepalive", ollamaKeepAliveField.getText().trim());
        s.aiConfig.put("ollama.model.embeddings",
                Objects.toString(ollamaEmbeddingsModelCombo.getSelectedItem(), "").trim());
        s.aiConfig.put("ollama.model.rerank",
                Objects.toString(ollamaRerankModelCombo.getSelectedItem(), "").trim());
        if (ollamaModelsEndpointField != null) {
            String ep = ollamaModelsEndpointField.getText().trim();
            s.aiConfig.put("ollama.endpoint.models", ep.isEmpty() ? "/api/tags" : ep);
        }
        // Proxy-Auth- und E2E-Credentials liegen jetzt global im Proxy-Tab
        // (Settings.proxyAuthUsername / proxyAuthPassword / proxyE2ePassword).
        // Die per-Tab Aktivierungs-Flags werden weiter unten als
        // aiConfig["useProxyAuth"] / aiConfig["useE2e"] persistiert.
        s.aiConfig.put("cloud.vendor", Objects.toString(cloudProviderField.getSelectedItem(), "OPENAI"));
        s.aiConfig.put("cloud.apikey", cloudApiKeyField.getText().trim());
        s.aiConfig.put("cloud.url", cloudApiUrlField.getText().trim());
        s.aiConfig.put("cloud.model", Objects.toString(cloudModelCombo.getSelectedItem(), "").trim());
        s.aiConfig.put("cloud.authHeader", cloudAuthHeaderField.getText().trim());
        s.aiConfig.put("cloud.authPrefix", cloudAuthPrefixField.getText().trim());
        s.aiConfig.put("cloud.anthropicVersion", cloudApiVersionField.getText().trim());
        s.aiConfig.put("cloud.organization", cloudOrgField.getText().trim());
        s.aiConfig.put("cloud.project", cloudProjectField.getText().trim());
        // Public Cloud embeddings & reranker (own URL + model)
        s.aiConfig.put("cloud.url.embeddings", cloudEmbeddingsUrlField.getText().trim());
        s.aiConfig.put("cloud.model.embeddings",
                Objects.toString(cloudEmbeddingsModelCombo.getSelectedItem(), "").trim());
        s.aiConfig.put("cloud.url.rerank", cloudRerankUrlField.getText().trim());
        s.aiConfig.put("cloud.model.rerank",
                Objects.toString(cloudRerankModelCombo.getSelectedItem(), "").trim());
        s.aiConfig.put("cloud.url.audio", cloudAudioUrlField.getText().trim());
        s.aiConfig.put("cloud.model.audio",
                Objects.toString(cloudAudioModelCombo.getSelectedItem(), "").trim());
        s.aiConfig.put("cloud.url.responses", cloudResponsesUrlField.getText().trim());
        s.aiConfig.put("cloud.url.responseById", cloudResponsesByIdUrlField.getText().trim());
        if (cloudModelsUrlField != null) {
            s.aiConfig.put("cloud.url.models", cloudModelsUrlField.getText().trim());
        }

        // Top-level Audio / Responses-API toggles (apply across all providers)
        s.aiConfig.put("ai.audio.enabled", String.valueOf(topAudioEnabledBox != null && topAudioEnabledBox.isSelected()));
        s.aiConfig.put("ai.responses.enabled", String.valueOf(topResponsesEnabledBox != null && topResponsesEnabledBox.isSelected()));

        // OpenAI Compatible: persist its own keys
        String oaicBase = oaicBaseUrlField.getText().trim();
        if (oaicBase.endsWith("/")) oaicBase = oaicBase.substring(0, oaicBase.length() - 1);
        String oaicApiKey = oaicApiKeyField.getText().trim();
        String oaicModel = Objects.toString(oaicModelCombo.getSelectedItem(), "").trim();
        String oaicEmbModel = Objects.toString(oaicEmbeddingsModelCombo.getSelectedItem(), "").trim();
        String oaicRerankModel = Objects.toString(oaicRerankModelCombo.getSelectedItem(), "").trim();
        s.aiConfig.put("openaiCompatible.baseUrl", oaicBase);
        s.aiConfig.put("openaiCompatible.apikey", oaicApiKey);
        s.aiConfig.put("openaiCompatible.model", oaicModel);
        s.aiConfig.put("openaiCompatible.model.embeddings", oaicEmbModel);
        s.aiConfig.put("openaiCompatible.model.rerank", oaicRerankModel);
        s.aiConfig.put("openaiCompatible.model.audio",
                Objects.toString(oaicAudioModelCombo.getSelectedItem(), "").trim());
        for (String[] ep : OAI_ENDPOINT_DEFAULTS) {
            String value = oaicEndpointFields.get(ep[0]).getText().trim();
            if (value.isEmpty()) value = ep[1];
            s.aiConfig.put("openaiCompatible.endpoint." + ep[0], value);
        }

        // Custom: persist its own keys (fully manual — no vendor preset, no fixed auth fields)
        String customBase = customBaseUrlField.getText().trim();
        if (customBase.endsWith("/")) customBase = customBase.substring(0, customBase.length() - 1);
        String customModel = Objects.toString(customModelCombo.getSelectedItem(), "").trim();
        String customEmbModel = Objects.toString(customEmbeddingsModelCombo.getSelectedItem(), "").trim();
        String customRerankModel = Objects.toString(customRerankModelCombo.getSelectedItem(), "").trim();
        // Drop all legacy custom.* auth keys — the headers table replaces them
        s.aiConfig.remove("custom.vendor");
        s.aiConfig.remove("custom.apikey");
        s.aiConfig.remove("custom.authHeader");
        s.aiConfig.remove("custom.authPrefix");
        s.aiConfig.remove("custom.anthropicVersion");
        s.aiConfig.put("custom.baseUrl", customBase);
        s.aiConfig.put("custom.model", customModel);
        s.aiConfig.put("custom.model.embeddings", customEmbModel);
        s.aiConfig.put("custom.model.rerank", customRerankModel);
        s.aiConfig.put("custom.model.audio",
                Objects.toString(customAudioModelCombo.getSelectedItem(), "").trim());
        for (String[] ep : OAI_ENDPOINT_DEFAULTS) {
            String value = customEndpointFields.get(ep[0]).getText().trim();
            if (value.isEmpty()) value = ep[1];
            s.aiConfig.put("custom.endpoint." + ep[0], value);
        }
        // Persist headers table — wipe previous, then write each non-empty row
        if (customHeadersTable.isEditing()) customHeadersTable.getCellEditor().stopCellEditing();
        s.aiConfig.keySet().removeIf(k -> k.startsWith("custom.header."));
        java.util.LinkedHashMap<String, String> customHeadersMap = new java.util.LinkedHashMap<>();
        for (int i = 0; i < customHeadersModel.getRowCount(); i++) {
            String name = Objects.toString(customHeadersModel.getValueAt(i, 0), "").trim();
            String value = Objects.toString(customHeadersModel.getValueAt(i, 1), "");
            if (name.isEmpty()) continue;
            customHeadersMap.put(name, value);
            s.aiConfig.put("custom.header." + name, value);
        }

        // Drop legacy privateCloud.* keys (data now lives in openaiCompatible.* / custom.*),
        // but keep one new key for the sub-mode the user picked inside the Private Cloud card.
        s.aiConfig.keySet().removeIf(k -> k.startsWith("privateCloud."));
        boolean isCustomMode = "Custom".equals(privateModeCombo.getSelectedItem());
        s.aiConfig.put("privateCloud.mode", isCustomMode ? "custom" : "compatible");

        // Mirror the active Private Cloud sub-mode config into cloud.* for CloudChatManager.
        // Always wipe stale cloud.header.* first so Public Cloud / Compatible mode aren't polluted.
        s.aiConfig.keySet().removeIf(k -> k.startsWith("cloud.header."));
        if (activeProvider == AiProvider.PRIVATE_CLOUD) {
            if (!isCustomMode) {
                String chatPath = oaicEndpointFields.get("chat").getText().trim();
                if (chatPath.isEmpty()) chatPath = "/v1/chat/completions";
                if (!chatPath.startsWith("/")) chatPath = "/" + chatPath;
                s.aiConfig.put("cloud.vendor", "OPENAI");
                s.aiConfig.put("cloud.url", oaicBase.isEmpty() ? "" : oaicBase + chatPath);
                s.aiConfig.put("cloud.apikey", oaicApiKey);
                s.aiConfig.put("cloud.model", oaicModel);
                s.aiConfig.put("cloud.model.embeddings", oaicEmbModel);
                s.aiConfig.put("cloud.model.rerank", oaicRerankModel);
                s.aiConfig.put("cloud.authHeader", "Authorization");
                s.aiConfig.put("cloud.authPrefix", "Bearer");
            } else {
                String chatPath = customEndpointFields.get("chat").getText().trim();
                if (chatPath.isEmpty()) chatPath = "/v1/chat/completions";
                if (!chatPath.startsWith("/")) chatPath = "/" + chatPath;
                s.aiConfig.put("cloud.vendor", "OPENAI");
                s.aiConfig.put("cloud.url", customBase.isEmpty() ? "" : customBase + chatPath);
                s.aiConfig.put("cloud.model", customModel);
                s.aiConfig.put("cloud.model.embeddings", customEmbModel);
                s.aiConfig.put("cloud.model.rerank", customRerankModel);
                // Mirror custom HTTP headers into cloud.header.* (CloudChatManager picks these up and
                // they override the default Auth logic).
                s.aiConfig.keySet().removeIf(k -> k.startsWith("cloud.header."));
                for (java.util.Map.Entry<String, String> h : customHeadersMap.entrySet()) {
                    s.aiConfig.put("cloud.header." + h.getKey(), h.getValue());
                }
                // Clear legacy single-header keys so they don't conflict with the table-driven headers
                s.aiConfig.put("cloud.apikey", "");
                s.aiConfig.put("cloud.authHeader", "");
                s.aiConfig.put("cloud.authPrefix", "");
            }
        }

        s.aiConfig.put("llama.enabled", String.valueOf(llamaEnabledBox.isSelected()));
        s.aiConfig.put("llama.binary", llamaBinaryField.getText().trim());
        s.aiConfig.put("llama.model", llamaModelField.getText().trim());
        s.aiConfig.put("llama.port", llamaPortSpinner.getValue().toString());
        s.aiConfig.put("llama.threads", llamaThreadsSpinner.getValue().toString());
        s.aiConfig.put("llama.context", llamaContextSpinner.getValue().toString());
        s.aiConfig.put("llama.temp", llamaTempField.getText().trim());
        s.aiConfig.put("llama.streaming", String.valueOf(llamaStreamingBox.isSelected()));
        s.aiConfig.put("llama.model.embeddings", llamaEmbeddingsModelField.getText().trim());
        s.aiConfig.put("llama.model.rerank", llamaRerankModelField.getText().trim());
        // LocalAI (skeleton — values are persisted; chat impl follows)
        s.aiConfig.put("localai.url", localAiBaseUrlField.getText().trim());
        s.aiConfig.put("localai.model", localAiModelField.getText().trim());
        s.aiConfig.put("localai.url.embeddings", localAiEmbeddingsUrlField.getText().trim());
        s.aiConfig.put("localai.model.embeddings", localAiEmbeddingsModelField.getText().trim());
        s.aiConfig.put("localai.url.rerank", localAiRerankUrlField.getText().trim());
        s.aiConfig.put("localai.model.rerank", localAiRerankModelField.getText().trim());
        s.aiConfig.put("localai.url.audio", localAiAudioUrlField.getText().trim());
        s.aiConfig.put("localai.model.audio", localAiAudioModelField.getText().trim());
        // ONNX Runtime
        s.aiConfig.put("onnx.model.path", onnxModelPathField.getText().trim());
        s.aiConfig.put("onnx.execution.provider",
                Objects.toString(onnxExecutionProviderCombo.getSelectedItem(), "directml"));
        s.aiConfig.put("onnx.max.tokens", onnxMaxTokensSpinner.getValue().toString());
        s.aiConfig.put("onnx.temperature", onnxTemperatureField.getText().trim());
        s.aiConfig.put("onnx.top.p", onnxTopPField.getText().trim());
        s.aiConfig.put("onnx.top.k", onnxTopKSpinner.getValue().toString());
        s.aiConfig.put("onnx.model.embeddings.path", onnxEmbeddingsModelPathField.getText().trim());
        s.aiConfig.put("onnx.model.rerank.path", onnxRerankModelPathField.getText().trim());
    }

    // ──── private helpers ────


    private void applyCloudVendorDefaults(boolean clearOptionalFields) {
        String vendor = Objects.toString(cloudProviderField.getSelectedItem(), "OPENAI");
        cloudApiUrlField.setText(cloudDefaultForVendor(vendor, "url"));
        cloudModelCombo.setSelectedItem(cloudDefaultForVendor(vendor, "model"));
        cloudAuthHeaderField.setText(cloudDefaultForVendor(vendor, "authHeader"));
        cloudAuthPrefixField.setText(cloudDefaultForVendor(vendor, "authPrefix"));
        cloudApiVersionField.setText(cloudDefaultForVendor(vendor, "anthropicVersion"));
        boolean isOpenAi = "OPENAI".equals(vendor);
        boolean isClaude = "CLAUDE".equals(vendor);
        cloudOrgField.setEnabled(isOpenAi);
        cloudProjectField.setEnabled(isOpenAi);
        cloudApiVersionField.setEnabled(isClaude);
        if (clearOptionalFields && !isOpenAi) { cloudOrgField.setText(""); cloudProjectField.setText(""); }
    }

    private static String cloudDefaultForVendor(String vendor, String key) {
        switch (vendor) {
            case "PERPLEXITY":
                if ("url".equals(key)) return "https://api.perplexity.ai/chat/completions";
                if ("baseUrl".equals(key)) return "https://api.perplexity.ai";
                if ("model".equals(key)) return "sonar";
                break;
            case "GROK":
                if ("url".equals(key)) return "https://api.x.ai/v1/chat/completions";
                if ("baseUrl".equals(key)) return "https://api.x.ai";
                if ("model".equals(key)) return "grok-2-latest";
                break;
            case "GEMINI":
                if ("url".equals(key)) return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
                if ("baseUrl".equals(key)) return "https://generativelanguage.googleapis.com/v1beta/openai";
                if ("model".equals(key)) return "gemini-2.0-flash";
                break;
            case "CLAUDE":
                if ("url".equals(key)) return "https://api.anthropic.com/v1/messages";
                if ("baseUrl".equals(key)) return "https://api.anthropic.com";
                if ("model".equals(key)) return "claude-3-5-sonnet-latest";
                if ("authHeader".equals(key)) return "x-api-key";
                if ("authPrefix".equals(key)) return "";
                if ("anthropicVersion".equals(key)) return "2023-06-01";
                break;
            case "OPENAI": default:
                if ("url".equals(key)) return "https://api.openai.com/v1/chat/completions";
                if ("baseUrl".equals(key)) return "https://api.openai.com";
                if ("model".equals(key)) return "gpt-4o-mini";
                break;
        }
        if ("authHeader".equals(key)) return "Authorization";
        if ("authPrefix".equals(key)) return "Bearer";
        if ("anthropicVersion".equals(key)) return "2023-06-01";
        return "";
    }

    /**
     * Creates a tiny square reset button (↺) with no padding/margin, fixed 22×22 px.
     */
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
     * Small gray italic info label for "feature not supported by this provider" notices.
     * Returned label is wide-spanning and is added to the relevant dependent-fields list
     * by the caller so the top-level toggle still grays it out for consistency.
     */
    private static JLabel unsupportedInfoLabel(String text) {
        JLabel l = new JLabel("<html><i>ℹ\u00a0" + text + "</i></html>");
        l.setForeground(new Color(120, 120, 120));
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        return l;
    }

    /** Lookup default OpenAI-compatible endpoint path for the given key. */
    private static String defaultEndpoint(String key) {
        for (String[] ep : OAI_ENDPOINT_DEFAULTS) {
            if (ep[0].equals(key)) return ep[1];
        }
        return "";
    }

    /** Creates a 30-column endpoint JTextField pre-filled with the default for {@code key}. */
    private static JTextField makeEndpointField(String key) {
        return new JTextField(defaultEndpoint(key), 30);
    }

    /**
     * Creates a small toggle button that shows/hides the content of a JPasswordField.
     * Default state: password hidden (echo char = '●').
     */
    private static JButton createPasswordToggle(JPasswordField field) {
        final char defaultEcho = field.getEchoChar() != 0 ? field.getEchoChar() : '●';
        JButton btn = new JButton("👁");
        btn.setToolTipText("Passwort anzeigen/verbergen");
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFocusable(false);
        btn.addActionListener(e -> {
            if (field.getEchoChar() == 0) {
                // Currently visible → hide
                field.setEchoChar(defaultEcho);
                btn.setText("👁");
            } else {
                // Currently hidden → show
                field.setEchoChar((char) 0);
                btn.setText("🔒");
            }
        });
        return btn;
    }

    // ──── Modell-Abruf ────

    /**
     * Erzeugt einen einheitlichen "🔄"-Button zum Nachladen der Modellliste eines Combos.
     * Wird neben den Embeddings-/Reranker-/Audio-Dropdowns verwendet, damit Nutzer dort
     * die Liste auch nachträglich aktualisieren können, ohne erst zum Chat-Modell springen
     * zu müssen.
     */
    private JButton makeModelFetchButton(String tooltip, Runnable action) {
        JButton btn = new JButton("🔄");
        btn.setToolTipText(tooltip);
        btn.setMargin(new Insets(2, 4, 2, 4));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Per-Provider „🧪 Verbindung testen"-Buttons (Chat / Embeddings / Reranker)
    //  Bauen aus dem aktuellen Feldzustand einen Snapshot, holen den passenden
    //  Test-Plan via ProviderDefinitions.testPlanFor(...) und delegieren an
    //  ConnectionTester.testAsync(...).
    // ─────────────────────────────────────────────────────────────────────

    private JPanel makeProviderTestRow(final AiProvider provider,
                                       final java.util.function.Supplier<java.util.Map<String, String>> snapshot,
                                       final de.bund.zrb.ui.settings.provider.Facet... facets) {
        JPanel row = new JPanel(new BorderLayout(4, 4));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        final JLabel status = new JLabel(" ");
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));
        for (final de.bund.zrb.ui.settings.provider.Facet f : facets) {
            final JButton b = new JButton("🧪 " + testLabel(f));
            b.setToolTipText("Verbindung mit dem " + testLabel(f) + "-Endpunkt testen");
            b.addActionListener(e -> {
                de.bund.zrb.ui.settings.provider.ConnectionTestPlan plan =
                        de.bund.zrb.ui.settings.provider.ProviderDefinitions
                                .testPlanFor(provider, f, snapshot.get());
                boolean up = useProxyBox == null || useProxyBox.isSelected();
                de.bund.zrb.ui.settings.provider.ConnectionTester.testAsync(status, b, plan, up);
            });
            buttons.add(b);
        }
        row.add(buttons, BorderLayout.NORTH);
        row.add(status, BorderLayout.CENTER);
        return row;
    }

    private static String testLabel(de.bund.zrb.ui.settings.provider.Facet f) {
        switch (f) {
            case CHAT:       return "Chat";
            case EMBEDDINGS: return "Embeddings";
            case RERANKER:   return "Reranker";
            case AUDIO:      return "Audio";
            default:         return f.name();
        }
    }

    private java.util.Map<String, String> snapshotOllama() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("ollama.url",              ollamaUrlField.getText().trim());
        m.put("ollama.model",            comboText(ollamaModelCombo));
        m.put("ollama.model.embeddings", comboText(ollamaEmbeddingsModelCombo));
        m.put("ollama.model.rerank",     comboText(ollamaRerankModelCombo));
        m.put("ollama.endpoint.models",  ollamaModelsEndpointField != null
                ? ollamaModelsEndpointField.getText().trim() : "/api/tags");
        return m;
    }

    private java.util.Map<String, String> snapshotCloud() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("cloud.vendor",            String.valueOf(cloudProviderField.getSelectedItem()));
        m.put("cloud.url",               cloudApiUrlField.getText().trim());
        m.put("cloud.apikey",            cloudApiKeyField.getText().trim());
        m.put("cloud.authHeader",        cloudAuthHeaderField.getText().trim());
        m.put("cloud.authPrefix",        cloudAuthPrefixField.getText().trim());
        m.put("cloud.anthropicVersion",  cloudApiVersionField.getText().trim());
        m.put("cloud.organization",      cloudOrgField.getText().trim());
        m.put("cloud.project",           cloudProjectField.getText().trim());
        m.put("cloud.model",             comboText(cloudModelCombo));
        m.put("cloud.url.embeddings",    cloudEmbeddingsUrlField.getText().trim());
        m.put("cloud.model.embeddings",  comboText(cloudEmbeddingsModelCombo));
        m.put("cloud.url.rerank",        cloudRerankUrlField.getText().trim());
        m.put("cloud.model.rerank",      comboText(cloudRerankModelCombo));
        m.put("cloud.url.models",        cloudModelsUrlField != null ? cloudModelsUrlField.getText().trim() : "");
        return m;
    }

    private java.util.Map<String, String> snapshotOaic() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("openaiCompatible.baseUrl",            oaicBaseUrlField.getText().trim());
        m.put("openaiCompatible.apikey",             oaicApiKeyField.getText().trim());
        m.put("openaiCompatible.model",              comboText(oaicModelCombo));
        m.put("openaiCompatible.model.embeddings",   comboText(oaicEmbeddingsModelCombo));
        m.put("openaiCompatible.model.rerank",       comboText(oaicRerankModelCombo));
        for (java.util.Map.Entry<String, JTextField> e : oaicEndpointFields.entrySet()) {
            m.put("openaiCompatible.endpoint." + e.getKey(), e.getValue().getText().trim());
        }
        return m;
    }

    private java.util.Map<String, String> snapshotCustom() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("custom.baseUrl",            customBaseUrlField.getText().trim());
        m.put("custom.model",              comboText(customModelCombo));
        m.put("custom.model.embeddings",   comboText(customEmbeddingsModelCombo));
        m.put("custom.model.rerank",       comboText(customRerankModelCombo));
        for (java.util.Map.Entry<String, JTextField> e : customEndpointFields.entrySet()) {
            m.put("custom.endpoint." + e.getKey(), e.getValue().getText().trim());
        }
        if (customHeadersModel != null) {
            for (int i = 0; i < customHeadersModel.getRowCount(); i++) {
                String name = String.valueOf(customHeadersModel.getValueAt(i, 0)).trim();
                String value = String.valueOf(customHeadersModel.getValueAt(i, 1));
                if (!name.isEmpty()) m.put("custom.header." + name, value);
            }
        }
        return m;
    }

    private java.util.Map<String, String> snapshotLocalAi() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        // localAiCard rendert über ProviderCardRenderer; getComponent(...) liefert die Felder.
        m.put("localai.url",              localAiBaseUrlField != null ? localAiBaseUrlField.getText().trim() : "");
        m.put("localai.model",            localAiModelField != null ? localAiModelField.getText().trim() : "");
        m.put("localai.url.embeddings",   localAiEmbeddingsUrlField != null ? localAiEmbeddingsUrlField.getText().trim() : "");
        m.put("localai.model.embeddings", localAiEmbeddingsModelField != null ? localAiEmbeddingsModelField.getText().trim() : "");
        m.put("localai.url.rerank",       localAiRerankUrlField != null ? localAiRerankUrlField.getText().trim() : "");
        m.put("localai.model.rerank",     localAiRerankModelField != null ? localAiRerankModelField.getText().trim() : "");
        return m;
    }

    private java.util.Map<String, String> snapshotLlama() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("llama.port", String.valueOf(llamaPortSpinner.getValue()));
        return m;
    }

    private static String comboText(JComboBox<String> c) {
        if (c == null) return "";
        Object sel = c.getEditor() != null ? c.getEditor().getItem() : c.getSelectedItem();
        return sel == null ? "" : sel.toString().trim();
    }

    /**
     * Ruft verfügbare Modelle von Ollama ab und befüllt das Dropdown.
     */
    private void fetchOllamaModels() {
        String url = ollamaUrlField.getText().trim();
        if (url.contains("/api/")) {
            url = url.substring(0, url.indexOf("/api/"));
        }
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        String path = ollamaModelsEndpointField != null
                ? ollamaModelsEndpointField.getText().trim() : "/api/tags";
        if (path.isEmpty()) path = "/api/tags";
        if (!path.startsWith("/")) path = "/" + path;
        fetchModelsAsync(ollamaModelCombo, ollamaModelStatusLabel, url + path,
                (java.util.Map<String, String>) null,
                ollamaEmbeddingsModelCombo, ollamaRerankModelCombo);
    }

    /** Ruft verfügbare Modelle vom OpenAI-Compatible-Endpunkt ab (GET {base}{modelsPath}). */
    private void fetchOaicModels() {
        String base = oaicBaseUrlField.getText().trim();
        if (base.isEmpty()) {
            oaicModelStatusLabel.setText("⚠️ Bitte zuerst die Base URL setzen.");
            oaicModelStatusLabel.setForeground(new Color(180, 100, 0));
            return;
        }
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String modelsPath = oaicEndpointFields.get("models").getText().trim();
        if (modelsPath.isEmpty()) modelsPath = "/v1/models";
        if (!modelsPath.startsWith("/")) modelsPath = "/" + modelsPath;
        String apiKey = oaicApiKeyField.getText().trim();
        String authValue = apiKey.isEmpty() ? null : "Bearer " + apiKey;
        java.util.LinkedHashMap<String, String> oaicHeaders = new java.util.LinkedHashMap<>();
        if (authValue != null) oaicHeaders.put("Authorization", authValue);
        fetchModelsAsync(oaicModelCombo, oaicModelStatusLabel, base + modelsPath, oaicHeaders,
                oaicEmbeddingsModelCombo, oaicRerankModelCombo, oaicAudioModelCombo);
    }

    /** Ruft verfügbare Modelle vom Custom-Endpunkt ab (GET {base}{modelsPath}) mit allen konfigurierten Headern. */
    private void fetchCustomModels() {
        if (customHeadersTable.isEditing()) customHeadersTable.getCellEditor().stopCellEditing();
        String base = customBaseUrlField.getText().trim();
        if (base.isEmpty()) {
            customModelStatusLabel.setText("⚠️ Bitte zuerst die Base URL setzen.");
            customModelStatusLabel.setForeground(new Color(180, 100, 0));
            return;
        }
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String modelsPath = customEndpointFields.get("models").getText().trim();
        if (modelsPath.isEmpty()) modelsPath = "/v1/models";
        if (!modelsPath.startsWith("/")) modelsPath = "/" + modelsPath;
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        for (int i = 0; i < customHeadersModel.getRowCount(); i++) {
            String name = Objects.toString(customHeadersModel.getValueAt(i, 0), "").trim();
            String value = Objects.toString(customHeadersModel.getValueAt(i, 1), "");
            if (!name.isEmpty()) headers.put(name, value);
        }
        fetchModelsAsync(customModelCombo, customModelStatusLabel, base + modelsPath, headers,
                customEmbeddingsModelCombo, customRerankModelCombo, customAudioModelCombo);
    }

    /**
     * Ruft verfügbare Modelle vom konfigurierten Cloud-Anbieter ab.
     */
    private void fetchCloudModels() {
        String vendor = Objects.toString(cloudProviderField.getSelectedItem(), "OPENAI");
        String apiKey = cloudApiKeyField.getText().trim();
        String apiUrl = cloudApiUrlField.getText().trim();
        String override = cloudModelsUrlField != null ? cloudModelsUrlField.getText().trim() : "";

        String modelsUrl;
        String authHeader = null;
        String authValue = null;

        if (!override.isEmpty()) {
            // Explizites Override hat Vorrang vor jeglicher Ableitung.
            modelsUrl = override;
            if (!apiKey.isEmpty()
                    && !override.contains("?key=") && !override.contains("&key=")) {
                authHeader = "Authorization";
                authValue = "Bearer " + apiKey;
            }
        } else switch (vendor) {
            case "OPENAI":
                modelsUrl = deriveModelsUrl(apiUrl, "/v1/models");
                authHeader = "Authorization";
                authValue = "Bearer " + apiKey;
                break;
            case "GROK":
                modelsUrl = "https://api.x.ai/v1/models";
                authHeader = "Authorization";
                authValue = "Bearer " + apiKey;
                break;
            case "GEMINI":
                modelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
                break;
            case "PERPLEXITY":
            case "CLAUDE":
                cloudModelStatusLabel.setText("ℹ️ Kein Modell-Endpunkt für " + vendor);
                cloudModelStatusLabel.setForeground(Color.DARK_GRAY);
                return;
            default:
                modelsUrl = deriveModelsUrl(apiUrl, "/v1/models");
                authHeader = "Authorization";
                authValue = "Bearer " + apiKey;
        }

        java.util.LinkedHashMap<String, String> cloudHeaders = new java.util.LinkedHashMap<>();
        if (authHeader != null && authValue != null) cloudHeaders.put(authHeader, authValue);
        fetchModelsAsync(cloudModelCombo, cloudModelStatusLabel, modelsUrl, cloudHeaders,
                cloudEmbeddingsModelCombo, cloudRerankModelCombo, cloudAudioModelCombo);
    }

    /**
     * Führt den HTTP-Abruf im Hintergrund aus und befüllt das Dropdown.
     */
    private void fetchModelsAsync(final JComboBox<String> combo,
                                  final JLabel statusLabel,
                                  final String tagsUrl,
                                  final String authHeader,
                                  final String authValue) {
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        if (authHeader != null && !authHeader.isEmpty() && authValue != null && !authValue.isEmpty()) {
            headers.put(authHeader, authValue);
        }
        fetchModelsAsync(combo, statusLabel, tagsUrl, headers, (JComboBox<String>[]) null);
    }

    /** Fetches the model list and populates {@code combo} plus any {@code additionalCombos} (e.g. embeddings/rerank). */
    @SafeVarargs
    private final void fetchModelsAsync(final JComboBox<String> combo,
                                        final JLabel statusLabel,
                                        final String tagsUrl,
                                        final java.util.Map<String, String> headers,
                                        final JComboBox<String>... additionalCombos) {
        statusLabel.setText("⏳ Lade Modelle...");
        statusLabel.setForeground(Color.BLACK);

        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                java.net.URL u = new java.net.URL(tagsUrl);
                java.net.Proxy proxy = resolveTabProxy(tagsUrl);
                conn = (java.net.HttpURLConnection) (proxy != null
                        ? u.openConnection(proxy) : u.openConnection());
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (headers != null) {
                    for (java.util.Map.Entry<String, String> h : headers.entrySet()) {
                        if (h.getKey() == null || h.getKey().isEmpty()) continue;
                        if (h.getValue() == null) continue;
                        conn.setRequestProperty(h.getKey(), h.getValue());
                    }
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    final String body;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        body = sb.toString();
                    }

                    final List<String> models = parseModelsResponse(body);
                    final String current = Objects.toString(combo.getSelectedItem(), "");

                    SwingUtilities.invokeLater(() -> {
                        combo.removeAllItems();
                        for (String m : models) {
                            combo.addItem(m);
                        }
                        combo.setSelectedItem(current);
                        if (additionalCombos != null) {
                            for (JComboBox<String> extra : additionalCombos) {
                                if (extra == null) continue;
                                String prev = Objects.toString(extra.getSelectedItem(), "");
                                extra.removeAllItems();
                                for (String m : models) extra.addItem(m);
                                extra.setSelectedItem(prev);
                            }
                        }
                        statusLabel.setText("✅ " + models.size() + " Modelle geladen");
                        statusLabel.setForeground(new Color(0, 128, 0));
                    });
                } else {
                    final int finalCode = code;
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("❌ HTTP " + finalCode);
                        statusLabel.setForeground(Color.RED);
                    });
                }
            } catch (Exception ex) {
                final String msg = ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("❌ " + msg);
                    statusLabel.setForeground(Color.RED);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * Löst den Proxy für eine Ziel-URL anhand des per-Tab {@code useProxyBox}-Schalters
     * und der globalen Proxy-Einstellungen auf. Liefert {@code null}, falls direkt
     * verbunden werden soll (Checkbox aus, DISABLED, lokaler Host oder Fehler).
     */
    private java.net.Proxy resolveTabProxy(String targetUrl) {
        try {
            boolean useProxy = useProxyBox != null && useProxyBox.isSelected();
            de.bund.zrb.net.ProxyResolver.ProxyResolution res =
                    de.bund.zrb.net.ProxyResolver.resolveForUrl(
                            targetUrl, de.bund.zrb.helper.SettingsHelper.load(), useProxy);
            if (res == null || res.isDirect()) return null;
            java.net.Proxy p = res.getProxy();
            return (p == null || p == java.net.Proxy.NO_PROXY) ? null : p;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parst die JSON-Antwort eines Modell-Endpunkts (Ollama- oder OpenAI-Format).
     */
    private List<String> parseModelsResponse(String json) {
        List<String> names = new ArrayList<>();
        try {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);

            if (obj.has("models") && obj.get("models").isJsonArray()) {
                JsonArray models = obj.getAsJsonArray("models");
                for (JsonElement e : models) {
                    if (e.isJsonObject()) {
                        JsonObject model = e.getAsJsonObject();
                        if (model.has("name")) {
                            String n = model.get("name").getAsString();
                            if (n.startsWith("models/")) n = n.substring("models/".length());
                            names.add(n);
                        }
                    }
                }
            } else if (obj.has("data") && obj.get("data").isJsonArray()) {
                JsonArray data = obj.getAsJsonArray("data");
                for (JsonElement e : data) {
                    if (e.isJsonObject()) {
                        JsonObject model = e.getAsJsonObject();
                        if (model.has("id")) {
                            names.add(model.get("id").getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Parse-Fehler: leere Liste
        }
        return names;
    }

    /**
     * Leitet die Modell-Auflistungs-URL von einer API-URL ab.
     */
    private String deriveModelsUrl(String apiUrl, String modelsPath) {
        if (apiUrl.contains("/v1/")) {
            return apiUrl.substring(0, apiUrl.indexOf("/v1/")) + modelsPath;
        }
        if (apiUrl.contains("/v2/")) {
            return apiUrl.substring(0, apiUrl.indexOf("/v2/")) + modelsPath;
        }
        int lastSlash = apiUrl.lastIndexOf('/');
        return lastSlash > 8 ? apiUrl.substring(0, lastSlash) + modelsPath : apiUrl + modelsPath;
    }

    /** Loads a {@link ModelManifest} from a classpath resource, returning {@code null} on failure. */
    private static ModelManifest loadManifest(String resourcePath) {
        try {
            return ModelManifest.fromResource(resourcePath);
        } catch (Exception e) {
            Logger.getLogger(AiSettingsPanel.class.getName())
                    .warning("Konnte Manifest nicht laden: " + resourcePath + " – " + e.getMessage());
            return null;
        }
    }
}

