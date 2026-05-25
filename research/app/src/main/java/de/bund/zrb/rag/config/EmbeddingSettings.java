package de.bund.zrb.rag.config;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;

import java.util.Map;

/**
 * Configuration for embedding generation.
 * Separate from AI settings to allow different providers.
 */
public class EmbeddingSettings {

    private AiProvider provider = AiProvider.OLLAMA;
    private String model = "nomic-embed-text";
    private String apiKey = "";
    private String baseUrl = "http://localhost:11434";
    private boolean useProxy = false;
    private String proxyHost = "";
    private int proxyPort = 0;
    private int timeoutSeconds = 30;
    private int batchSize = 10;
    private boolean enabled = true;

    // Getters and setters

    public AiProvider getProvider() {
        return provider;
    }

    public EmbeddingSettings setProvider(AiProvider provider) {
        this.provider = provider;
        return this;
    }

    public String getModel() {
        return model;
    }

    public EmbeddingSettings setModel(String model) {
        this.model = model;
        return this;
    }

    public String getApiKey() {
        return apiKey;
    }

    public EmbeddingSettings setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public EmbeddingSettings setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public EmbeddingSettings setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
        return this;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public EmbeddingSettings setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public EmbeddingSettings setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        return this;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public EmbeddingSettings setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public EmbeddingSettings setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public EmbeddingSettings setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Get default embedding model for provider.
     */
    public static String getDefaultModel(AiProvider provider) {
        if (provider == null) return "nomic-embed-text";
        switch (provider) {
            case OLLAMA:
                return "nomic-embed-text";
            case CLOUD:
                return "text-embedding-3-small";
            case LOCAL_AI:
            case LLAMA_CPP_SERVER:
                return "all-minilm";
            case CUSTOM:
                return "nomic-embed-text"; // Standard fÃ¼r selbstgehostete Server
            case DISABLED:
            default:
                return "nomic-embed-text";
        }
    }

    /**
     * Get default base URL for provider.
     */
    public static String getDefaultBaseUrl(AiProvider provider) {
        if (provider == null) return "http://localhost:11434";
        switch (provider) {
            case OLLAMA:
                return "http://localhost:11434";
            case CLOUD:
                return "https://api.openai.com/v1";
            case LOCAL_AI:
                return "http://localhost:8080/v1";
            case LLAMA_CPP_SERVER:
                return "http://localhost:8080/v1";
            case CUSTOM:
                return "http://localhost:11434"; // Standard fÃ¼r selbstgehostete Server
            case DISABLED:
            default:
                return "http://localhost:11434";
        }
    }

    public static EmbeddingSettings defaults() {
        return new EmbeddingSettings();
    }

    /**
     * LÃ¤dt die EmbeddingSettings aus den gespeicherten Anwendungseinstellungen.
     *
     * <p>Quelle der Werte:</p>
     * <ul>
     *   <li>Bei {@code embeddingConfig.overwrite=true} stammen <b>sÃ¤mtliche</b>
     *       provider-spezifischen Felder (Provider, URL, Modell, API-Key, Auth-Header,
     *       Vendor, Ports, Modell-Pfade, â€¦) aus {@code embeddingConfig}. Nicht
     *       Ã¼berschrieben werden chat-/reranker-/audio-spezifische Felder anderer
     *       Facets.</li>
     *   <li>Bei {@code overwrite=false} stammen sie aus {@code aiConfig}.</li>
     *   <li>SchlÃ¼ssel-Schema ist in beiden Maps identisch (z.&nbsp;B.
     *       {@code ollama.url}, {@code cloud.apikey}, {@code cloud.url.embeddings},
     *       {@code cloud.model.embeddings}).</li>
     *   <li><b>Timeout, BatchSize, enabled</b> kommen IMMER aus {@code embeddingConfig}.</li>
     *   <li><b>Proxy</b> kommt aus dem zentralen Proxy-Tab.</li>
     * </ul>
     */
    public static EmbeddingSettings fromStoredConfig() {
        Settings settings = SettingsHelper.load();
        Map<String, String> embConfig = settings.embeddingConfig != null
                ? settings.embeddingConfig : new java.util.HashMap<String, String>();
        Map<String, String> aiConfig = settings.aiConfig != null
                ? settings.aiConfig : new java.util.HashMap<String, String>();

        boolean overwrite = Boolean.parseBoolean(embConfig.getOrDefault("overwrite", "false"));
        Map<String, String> source = overwrite ? embConfig : aiConfig;

        EmbeddingSettings result = new EmbeddingSettings();
        result.setEnabled(Boolean.parseBoolean(embConfig.getOrDefault("enabled", "true")));

        try {
            result.setTimeoutSeconds(Integer.parseInt(embConfig.getOrDefault("timeout", "30")));
        } catch (NumberFormatException e) {
            result.setTimeoutSeconds(30);
        }
        try {
            result.setBatchSize(Integer.parseInt(embConfig.getOrDefault("batchSize", "10")));
        } catch (NumberFormatException e) {
            result.setBatchSize(10);
        }

        AiProvider provider;
        try {
            provider = AiProvider.valueOf(source.getOrDefault("provider", "OLLAMA"));
        } catch (Exception e) {
            provider = AiProvider.OLLAMA;
        }
        result.setProvider(provider);

        applyEmbeddingFields(result, provider, source);

        if ("MANUAL".equals(settings.proxyMode)
                && settings.proxyHost != null && !settings.proxyHost.trim().isEmpty()) {
            result.setUseProxy(true);
            result.setProxyHost(settings.proxyHost);
            result.setProxyPort(settings.proxyPort);
        } else {
            result.setUseProxy(true);
        }

        return result;
    }

    /**
     * BefÃ¼llt URL, Modell und API-Key (sowie ggf. weitere credentials) aus der gegebenen
     * Map. SchlÃ¼ssel-Schema entspricht dem von {@code AiSettingsPanel} bzw.
     * {@code ProviderConfigPanel(EnumSet.of(Facet.EMBEDDINGS))}.
     */
    private static void applyEmbeddingFields(EmbeddingSettings result, AiProvider provider,
                                             Map<String, String> cfg) {
        switch (provider) {
            case OLLAMA: {
                result.setBaseUrl(firstNonEmpty(cfg.get("ollama.url"), "http://localhost:11434"));
                result.setModel(firstNonEmpty(
                        cfg.get("ollama.model.embeddings"),
                        cfg.get("ollama.model"),
                        "nomic-embed-text"));
                result.setApiKey("");
                break;
            }
            case CLOUD: {
                result.setBaseUrl(firstNonEmpty(
                        cfg.get("cloud.url.embeddings"),
                        cfg.get("cloud.url"),
                        ""));
                result.setModel(firstNonEmpty(
                        cfg.get("cloud.model.embeddings"),
                        cfg.get("cloud.model"),
                        ""));
                result.setApiKey(firstNonEmpty(cfg.get("cloud.apikey"), ""));
                break;
            }
            case PRIVATE_CLOUD: {
                String mode = cfg.getOrDefault("privateCloud.mode", "compatible");
                String prefix = "compatible".equals(mode) ? "openaiCompatible" : "custom";
                result.setBaseUrl(firstNonEmpty(
                        cfg.get(prefix + ".baseUrl"),
                        cfg.get(prefix + ".url"),
                        ""));
                result.setModel(firstNonEmpty(
                        cfg.get(prefix + ".model.embeddings"),
                        cfg.get(prefix + ".model"),
                        ""));
                result.setApiKey(firstNonEmpty(
                        cfg.get(prefix + ".apikey"),
                        cfg.get(prefix + ".apiKey"),
                        ""));
                break;
            }
            case LOCAL_AI: {
                result.setBaseUrl(firstNonEmpty(
                        cfg.get("localai.url.embeddings"),
                        cfg.get("localai.url"),
                        ""));
                result.setModel(firstNonEmpty(
                        cfg.get("localai.model.embeddings"),
                        cfg.get("localai.model"),
                        ""));
                result.setApiKey(firstNonEmpty(cfg.get("localai.apikey"), ""));
                break;
            }
            case LLAMA_CPP_SERVER: {
                try {
                    int port = Integer.parseInt(cfg.getOrDefault("llama.port", "8080"));
                    result.setBaseUrl("http://localhost:" + port);
                } catch (NumberFormatException e) {
                    result.setBaseUrl("http://localhost:8080");
                }
                result.setModel(firstNonEmpty(
                        cfg.get("llama.model.embeddings"),
                        cfg.get("llama.model"),
                        ""));
                result.setApiKey("");
                break;
            }
            case ONNX_RUNTIME: {
                result.setBaseUrl("");
                result.setModel(firstNonEmpty(
                        cfg.get("onnx.model.embeddings.path"),
                        cfg.get("onnx.model.path"),
                        ""));
                result.setApiKey("");
                break;
            }
            case DISABLED:
            default:
                result.setApiKey("");
                break;
        }
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return "";
    }
}
