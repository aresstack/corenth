package de.bund.zrb.summarizer;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;

import java.util.HashMap;
import java.util.Map;

/**
 * Konfiguration des {@link de.zrb.bund.api.SummarizerService}.
 *
 * <p>Folgt exakt demselben Schema wie {@code EmbeddingSettings} und
 * {@code RerankerSettings}:</p>
 * <ul>
 *   <li>{@code overwrite=false} → Provider/URL/Modell/API-Key werden aus dem
 *       AI-Tab "Allgemein" ({@code aiConfig}) genommen.</li>
 *   <li>{@code overwrite=true}  → Werte stammen aus {@code summarizerConfig}
 *       mit demselben Provider-prefixed Key-Schema (z.&nbsp;B. {@code ollama.url},
 *       {@code ollama.model.summarize}).</li>
 * </ul>
 *
 * <p>{@code enabled}, Token-Cap, Cache-Größe, System-Prompt usw. werden
 * <em>immer</em> aus {@code summarizerConfig} gelesen.</p>
 */
public final class SummarizerSettings {

    /** Default-System-Prompt — als statische Konstante, damit Konsumenten zurücksetzen können. */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "Du bist ein Code-Summarizer. Antworte ausschließlich in DEUTSCH, "
                    + "extrem kurz und sachlich. Keine Einleitung, keine Erklärung, "
                    + "keine Code-Wiederholung. Wenn ein bestimmter Stil verlangt wird "
                    + "(LABEL / SENTENCE / BULLETS), halte dich exakt daran. "
                    + "LABEL = eine Substantiv-Phrase (z.B. \"Validierung der Eingabe\"). "
                    + "SENTENCE = ein Satz. "
                    + "BULLETS = kurze Bullet-Liste mit \"- \" Präfix.";

    /** Default-Modell für den dedizierten Modus (kleines, CPU-taugliches Ollama-Modell). */
    public static final String DEFAULT_MODEL = "qwen2.5:0.5b";

    private boolean enabled;
    private boolean overwrite;

    private AiProvider provider;
    private String apiUrl;
    private String model;
    private String apiKey;

    private String systemPrompt;
    private int maxTokens;
    private int timeoutSeconds;
    private boolean cacheEnabled;
    private int cacheSize;

    /** Steuert, ob {@code SplitPreviewTab}/{@code OutlineToMermaidConverter} den Summarizer nutzen. */
    private boolean umlSummarizationEnabled;

    // ── Defaults ──────────────────────────────────────────────────

    public static SummarizerSettings defaults() {
        SummarizerSettings s = new SummarizerSettings();
        s.enabled        = false;
        s.overwrite      = false;
        s.provider       = AiProvider.OLLAMA;
        s.apiUrl         = "http://localhost:11434/api/chat";
        s.model          = DEFAULT_MODEL;
        s.apiKey         = "";
        s.systemPrompt   = DEFAULT_SYSTEM_PROMPT;
        s.maxTokens      = 64;
        s.timeoutSeconds = 15;
        s.cacheEnabled   = true;
        s.cacheSize      = 1000;
        s.umlSummarizationEnabled = true;
        return s;
    }

    // ── Getter / Setter ───────────────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public SummarizerSettings setEnabled(boolean v) { this.enabled = v; return this; }

    public boolean isOverwrite() { return overwrite; }
    public SummarizerSettings setOverwrite(boolean v) { this.overwrite = v; return this; }

    public AiProvider getProvider() { return provider; }
    public SummarizerSettings setProvider(AiProvider p) { this.provider = p; return this; }

    public String getApiUrl() { return apiUrl; }
    public SummarizerSettings setApiUrl(String v) { this.apiUrl = v; return this; }

    public String getModel() { return model; }
    public SummarizerSettings setModel(String v) { this.model = v; return this; }

    public String getApiKey() { return apiKey; }
    public SummarizerSettings setApiKey(String v) { this.apiKey = v; return this; }

    public String getSystemPrompt() { return systemPrompt; }
    public SummarizerSettings setSystemPrompt(String v) { this.systemPrompt = v; return this; }

    public int getMaxTokens() { return maxTokens; }
    public SummarizerSettings setMaxTokens(int v) { this.maxTokens = v; return this; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public SummarizerSettings setTimeoutSeconds(int v) { this.timeoutSeconds = v; return this; }

    public boolean isCacheEnabled() { return cacheEnabled; }
    public SummarizerSettings setCacheEnabled(boolean v) { this.cacheEnabled = v; return this; }

    public int getCacheSize() { return cacheSize; }
    public SummarizerSettings setCacheSize(int v) { this.cacheSize = v; return this; }

    public boolean isUmlSummarizationEnabled() { return umlSummarizationEnabled; }
    public SummarizerSettings setUmlSummarizationEnabled(boolean v) {
        this.umlSummarizationEnabled = v;
        return this;
    }

    // ── Factories ─────────────────────────────────────────────────

    public static SummarizerSettings fromStoredConfig() {
        Settings settings = SettingsHelper.load();
        return fromConfig(settings.summarizerConfig, settings.aiConfig);
    }

    public static SummarizerSettings fromConfig(Map<String, String> summarizerCfgIn,
                                                Map<String, String> aiCfgIn) {
        Map<String, String> cfg = summarizerCfgIn != null ? summarizerCfgIn : new HashMap<String, String>();
        Map<String, String> ai  = aiCfgIn != null ? aiCfgIn  : new HashMap<String, String>();

        SummarizerSettings r = defaults();
        r.setEnabled(Boolean.parseBoolean(cfg.getOrDefault("enabled", "false")));
        r.setOverwrite(Boolean.parseBoolean(cfg.getOrDefault("overwrite", "false")));
        r.setSystemPrompt(cfg.getOrDefault("systemPrompt", DEFAULT_SYSTEM_PROMPT));

        try { r.setMaxTokens(Integer.parseInt(cfg.getOrDefault("maxTokens", "64"))); }
        catch (NumberFormatException e) { /* keep */ }
        try { r.setTimeoutSeconds(Integer.parseInt(cfg.getOrDefault("timeout", "15"))); }
        catch (NumberFormatException e) { /* keep */ }

        r.setCacheEnabled(Boolean.parseBoolean(cfg.getOrDefault("cacheEnabled", "true")));
        try { r.setCacheSize(Integer.parseInt(cfg.getOrDefault("cacheSize", "1000"))); }
        catch (NumberFormatException e) { /* keep */ }

        r.setUmlSummarizationEnabled(
                Boolean.parseBoolean(cfg.getOrDefault("umlEnabled", "true")));

        Map<String, String> source = r.isOverwrite() ? cfg : ai;
        AiProvider provider;
        try {
            provider = AiProvider.valueOf(source.getOrDefault("provider", AiProvider.OLLAMA.name()));
        } catch (Exception e) {
            provider = AiProvider.OLLAMA;
        }
        r.setProvider(provider);

        applyProviderFields(r, provider, source);
        return r;
    }

    /**
     * Liest provider-spezifische Felder (URL / Modell / API-Key) aus der Quelle
     * und füllt {@code apiUrl}/{@code model}/{@code apiKey} so, dass die
     * Implementierung direkt einen HTTP-Call absetzen kann.
     */
    private static void applyProviderFields(SummarizerSettings r, AiProvider provider,
                                            Map<String, String> src) {
        switch (provider) {
            case OLLAMA: {
                String base = firstNonEmpty(src.get("ollama.url"), "http://localhost:11434");
                r.setApiUrl(stripApi(base) + "/api/chat");
                r.setModel(firstNonEmpty(
                        src.get("ollama.model.summarize"),
                        src.get("ollama.model"),
                        DEFAULT_MODEL));
                r.setApiKey("");
                break;
            }
            case CLOUD: {
                r.setApiUrl(firstNonEmpty(
                        src.get("cloud.url.summarize"),
                        src.get("cloud.url"),
                        ""));
                r.setModel(firstNonEmpty(
                        src.get("cloud.model.summarize"),
                        src.get("cloud.model"),
                        "gpt-4o-mini"));
                r.setApiKey(firstNonEmpty(src.get("cloud.apikey"), ""));
                break;
            }
            case PRIVATE_CLOUD: {
                String mode = src.getOrDefault("privateCloud.mode", "compatible");
                String prefix = "compatible".equals(mode) ? "openaiCompatible" : "custom";
                String base = firstNonEmpty(src.get(prefix + ".baseUrl"), "");
                String ep   = firstNonEmpty(
                        src.get(prefix + ".endpoint.summarize"),
                        src.get(prefix + ".endpoint.chat"),
                        "/v1/chat/completions");
                if (!base.isEmpty()) {
                    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                    if (!ep.startsWith("/")) ep = "/" + ep;
                    r.setApiUrl(base + ep);
                }
                r.setModel(firstNonEmpty(
                        src.get(prefix + ".model.summarize"),
                        src.get(prefix + ".model"),
                        DEFAULT_MODEL));
                r.setApiKey(firstNonEmpty(
                        src.get(prefix + ".apikey"),
                        src.get(prefix + ".apiKey"),
                        ""));
                break;
            }
            case LOCAL_AI: {
                r.setApiUrl(firstNonEmpty(
                        src.get("localai.url.summarize"),
                        src.get("localai.url"),
                        ""));
                r.setModel(firstNonEmpty(
                        src.get("localai.model.summarize"),
                        src.get("localai.model"),
                        DEFAULT_MODEL));
                r.setApiKey(firstNonEmpty(src.get("localai.apikey"), ""));
                break;
            }
            case LLAMA_CPP_SERVER: {
                int port;
                try { port = Integer.parseInt(src.getOrDefault("llama.port", "8080")); }
                catch (NumberFormatException e) { port = 8080; }
                r.setApiUrl("http://localhost:" + port + "/v1/chat/completions");
                r.setModel(firstNonEmpty(
                        src.get("llama.model.summarize"),
                        src.get("llama.model"),
                        ""));
                r.setApiKey("");
                break;
            }
            case ONNX_RUNTIME: {
                // Lokale ONNX-Inferenz: kein HTTP, eigene Modellpfad-Logik nötig.
                r.setApiUrl("");
                r.setModel(firstNonEmpty(
                        src.get("onnx.model.summarize.path"),
                        src.get("onnx.model.path"),
                        ""));
                r.setApiKey("");
                break;
            }
            case DISABLED:
            default:
                r.setApiUrl("");
                r.setModel("");
                r.setApiKey("");
                break;
        }
    }

    private static String stripApi(String url) {
        if (url == null) return "";
        int i = url.indexOf("/api/");
        return i > 0 ? url.substring(0, i) : url;
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }
}

