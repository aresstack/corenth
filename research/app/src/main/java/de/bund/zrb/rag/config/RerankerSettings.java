package de.bund.zrb.rag.config;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the optional cross-encoder reranker stage.
 *
 * <p>A reranker (e.g. BAAI/bge-reranker-v2-m3) scores each (query, passage) pair
 * with a cross-encoder model, producing much more accurate relevance scores than
 * the initial bi-encoder embedding similarity or BM25 lexical score.
 *
 * <p>The typical RAG pipeline becomes:
 * <ol>
 *   <li>BM25 retrieval (+ optional semantic/embedding search) → candidate set</li>
 *   <li><b>Reranker</b> → re-scores candidates on raw text, replaces BM25 scoring</li>
 *   <li>Top-K selection → final chunks for LLM context</li>
 * </ol>
 *
 * <p><b>Embeddings are NOT required.</b> The reranker works on raw text and can
 * rescore BM25-only candidates. Embeddings only widen the candidate pool
 * (semantic recall), while the reranker replaces the scoring step.
 *
 * <p>Supported providers:
 * <ul>
 *   <li><b>Ollama</b> — local, via {@code POST /api/embed} (requires an Ollama version
 *       that exposes reranking scores, or a cross-encoder model served as embedding model)</li>
 *   <li><b>Custom API</b> — any HTTP endpoint that accepts a JSON body with
 *       {@code query} and {@code documents} and returns a list of {@code score} values.
 *       Compatible with: Jina Reranker API, Cohere Rerank, TEI /rerank, vLLM /score, etc.</li>
 * </ul>
 *
 * <p>Recommended models:
 * <ul>
 *   <li>BAAI/bge-reranker-v2-m3 — multilingual, fast, excellent quality</li>
 *   <li>BAAI/bge-reranker-v2-gemma — larger, highest quality</li>
 *   <li>cross-encoder/ms-marco-MiniLM-L-6-v2 — English-only, very fast</li>
 * </ul>
 */
public class RerankerSettings {

    /** Whether the reranker stage is enabled. */
    private boolean enabled = false;

    /**
     * Base URL of the reranker API endpoint.
     * <ul>
     *   <li>TEI (Text Embeddings Inference): {@code http://localhost:8082/rerank}</li>
     *   <li>Jina: {@code https://api.jina.ai/v1/rerank}</li>
     *   <li>Cohere: {@code https://api.cohere.ai/v1/rerank}</li>
     *   <li>Custom: any URL that follows the rerank protocol</li>
     * </ul>
     */
    private String apiUrl = "http://localhost:8082/rerank";

    /** Model name sent with the request (e.g. {@code BAAI/bge-reranker-v2-m3}). */
    private String model = "BAAI/bge-reranker-v2-m3";

    /** Optional API key for authenticated endpoints (Jina, Cohere, etc.). */
    private String apiKey = "";

    /**
     * Number of top candidates to keep after reranking.
     * The hybrid retriever fetches {@code candidatePoolSize} results,
     * the reranker re-scores them, and finally the top {@code topN} are returned.
     */
    private int topN = 5;

    /**
     * Size of the candidate pool sent to the reranker.
     * Should be larger than {@code topN} — typically 3–5× larger.
     * The reranker scores all candidates and keeps only the best {@code topN}.
     */
    private int candidatePoolSize = 50;

    /** HTTP request timeout in seconds. */
    private int timeoutSeconds = 30;

    /** Whether to route requests through the configured proxy. */
    private boolean useProxy = false;

    /**
     * Minimum relevance score (0.0–1.0) for a reranked result to be included.
     * Chunks scoring below this threshold are discarded even if they are
     * within the topN limit. Set to 0.0 to disable threshold filtering.
     */
    private float scoreThreshold = 0.0f;

    // ── Getters & Setters (fluent) ──────────────────────────────────

    public boolean isEnabled() {
        return enabled;
    }

    public RerankerSettings setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public RerankerSettings setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
        return this;
    }

    public String getModel() {
        return model;
    }

    public RerankerSettings setModel(String model) {
        this.model = model;
        return this;
    }

    public String getApiKey() {
        return apiKey;
    }

    public RerankerSettings setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public int getTopN() {
        return topN;
    }

    public RerankerSettings setTopN(int topN) {
        this.topN = topN;
        return this;
    }

    public int getCandidatePoolSize() {
        return candidatePoolSize;
    }

    public RerankerSettings setCandidatePoolSize(int candidatePoolSize) {
        this.candidatePoolSize = candidatePoolSize;
        return this;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public RerankerSettings setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public RerankerSettings setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
        return this;
    }

    public float getScoreThreshold() {
        return scoreThreshold;
    }

    public RerankerSettings setScoreThreshold(float scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
        return this;
    }

    // ── Factory ─────────────────────────────────────────────────────

    public static RerankerSettings defaults() {
        return new RerankerSettings();
    }

    /**
     * Load reranker settings from the stored application configuration.
     *
     * <p>Source resolution mirrors {@link EmbeddingSettings#fromStoredConfig()}:</p>
     * <ul>
     *   <li>If {@code rerankerConfig.overwrite=true} → the provider/URL/model/apiKey fields
     *       are resolved from <b>{@code rerankerConfig}</b> using the same
     *       provider-prefixed key scheme as {@code aiConfig} (e.g. {@code ollama.url},
     *       {@code cloud.url.rerank}, {@code cloud.model.rerank},
     *       {@code openaiCompatible.baseUrl}, …).</li>
     *   <li>Otherwise the provider/URL/model/apiKey come from <b>{@code aiConfig}</b>.</li>
     *   <li>{@code enabled}, {@code topN}, {@code candidatePoolSize}, {@code timeout},
     *       {@code scoreThreshold} and {@code useProxy} are <em>always</em> read from
     *       {@code rerankerConfig}.</li>
     *   <li>Legacy flat keys ({@code apiUrl}/{@code model}/{@code apiKey}) are still honoured
     *       as a fallback for migrations.</li>
     * </ul>
     */
    public static RerankerSettings fromStoredConfig() {
        Settings settings = SettingsHelper.load();
        return fromConfig(settings.rerankerConfig, settings.aiConfig);
    }

    /**
     * Build {@link RerankerSettings} from in-memory configuration maps without touching
     * the persisted settings file. Used by the Reranker settings panel for the
     * connection-test workflow.
     */
    public static RerankerSettings fromConfig(Map<String, String> rerankerConfigIn,
                                              Map<String, String> aiConfigIn) {
        Map<String, String> rerankerConfig = rerankerConfigIn != null ? rerankerConfigIn : new HashMap<String, String>();
        Map<String, String> aiConfig = aiConfigIn != null ? aiConfigIn : new HashMap<String, String>();

        RerankerSettings result = new RerankerSettings();

        result.setEnabled(Boolean.parseBoolean(rerankerConfig.getOrDefault("enabled", "false")));

        try { result.setTopN(Integer.parseInt(rerankerConfig.getOrDefault("topN", "5"))); }
        catch (NumberFormatException e) { /* keep default */ }
        try { result.setCandidatePoolSize(Integer.parseInt(rerankerConfig.getOrDefault("candidatePoolSize", "50"))); }
        catch (NumberFormatException e) { /* keep default */ }
        try { result.setTimeoutSeconds(Integer.parseInt(rerankerConfig.getOrDefault("timeout", "30"))); }
        catch (NumberFormatException e) { /* keep default */ }
        try { result.setScoreThreshold(Float.parseFloat(rerankerConfig.getOrDefault("scoreThreshold", "0.0"))); }
        catch (NumberFormatException e) { /* keep default */ }

        result.setUseProxy(Boolean.parseBoolean(rerankerConfig.getOrDefault("useProxy", "false")));

        boolean overwrite = Boolean.parseBoolean(rerankerConfig.getOrDefault("overwrite", "false"));
        Map<String, String> source = overwrite ? rerankerConfig : aiConfig;

        // Legacy fallback: if the source has no provider key but rerankerConfig still
        // carries the legacy flat apiUrl/model/apiKey scheme, use those directly.
        if (!source.containsKey("provider")
                && (rerankerConfig.containsKey("apiUrl") || rerankerConfig.containsKey("model"))) {
            result.setApiUrl(rerankerConfig.getOrDefault("apiUrl", "http://localhost:8082/rerank"));
            result.setModel(rerankerConfig.getOrDefault("model", "BAAI/bge-reranker-v2-m3"));
            result.setApiKey(rerankerConfig.getOrDefault("apiKey", ""));
            return result;
        }

        AiProvider provider;
        try {
            provider = AiProvider.valueOf(source.getOrDefault("provider", "OLLAMA"));
        } catch (Exception e) {
            provider = AiProvider.OLLAMA;
        }
        applyRerankerFields(result, provider, source);
        return result;
    }

    /**
     * Resolves {@link #apiUrl}, {@link #model} and {@link #apiKey} from a generic
     * provider-prefixed config map (same scheme as {@code aiConfig} and the embeddings
     * override). Mirrors {@code EmbeddingSettings.applyEmbeddingFields(...)}.
     */
    private static void applyRerankerFields(RerankerSettings result, AiProvider provider,
                                            Map<String, String> cfg) {
        switch (provider) {
            case OLLAMA: {
                String base = firstNonEmpty(cfg.get("ollama.url"), "http://localhost:11434");
                // Ollama hat keinen eigenen Rerank-Endpunkt: man verwendet denselben
                // /api/embed-Endpunkt wie für Embeddings, lädt aber ein Reranker-/
                // Cross-Encoder-Modell. Das Scoring (Cosine-Similarity zwischen Query-
                // und Passage-Embedding) übernimmt der HttpRerankerClient.
                result.setApiUrl(stripApi(base) + "/api/embed");
                result.setModel(firstNonEmpty(
                        cfg.get("ollama.model.rerank"),
                        cfg.get("ollama.model"),
                        "BAAI/bge-reranker-v2-m3"));
                result.setApiKey("");
                break;
            }
            case CLOUD: {
                result.setApiUrl(firstNonEmpty(
                        cfg.get("cloud.url.rerank"),
                        cfg.get("cloud.url"),
                        "https://api.jina.ai/v1/rerank"));
                result.setModel(firstNonEmpty(
                        cfg.get("cloud.model.rerank"),
                        cfg.get("cloud.model"),
                        "BAAI/bge-reranker-v2-m3"));
                result.setApiKey(firstNonEmpty(cfg.get("cloud.apikey"), ""));
                break;
            }
            case PRIVATE_CLOUD: {
                String mode = cfg.getOrDefault("privateCloud.mode", "compatible");
                String prefix = "compatible".equals(mode) ? "openaiCompatible" : "custom";
                String base = firstNonEmpty(cfg.get(prefix + ".baseUrl"), "");
                String ep = firstNonEmpty(cfg.get(prefix + ".endpoint.rerank"), "/v1/rerank");
                if (!base.isEmpty()) {
                    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                    if (!ep.startsWith("/")) ep = "/" + ep;
                    result.setApiUrl(base + ep);
                }
                result.setModel(firstNonEmpty(
                        cfg.get(prefix + ".model.rerank"),
                        cfg.get(prefix + ".model"),
                        "BAAI/bge-reranker-v2-m3"));
                result.setApiKey(firstNonEmpty(
                        cfg.get(prefix + ".apikey"),
                        cfg.get(prefix + ".apiKey"),
                        ""));
                break;
            }
            case LOCAL_AI: {
                result.setApiUrl(firstNonEmpty(
                        cfg.get("localai.url.rerank"),
                        cfg.get("localai.url"),
                        ""));
                result.setModel(firstNonEmpty(
                        cfg.get("localai.model.rerank"),
                        cfg.get("localai.model"),
                        ""));
                result.setApiKey(firstNonEmpty(cfg.get("localai.apikey"), ""));
                break;
            }
            case LLAMA_CPP_SERVER: {
                try {
                    int port = Integer.parseInt(cfg.getOrDefault("llama.port", "8080"));
                    result.setApiUrl("http://localhost:" + port + "/v1/rerank");
                } catch (NumberFormatException e) {
                    result.setApiUrl("http://localhost:8080/v1/rerank");
                }
                result.setModel(firstNonEmpty(
                        cfg.get("llama.model.rerank"),
                        cfg.get("llama.model"),
                        ""));
                result.setApiKey("");
                break;
            }
            case ONNX_RUNTIME: {
                result.setApiUrl("");
                result.setModel(firstNonEmpty(
                        cfg.get("onnx.model.rerank.path"),
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

    private static String stripApi(String url) {
        if (url == null) return "";
        int i = url.indexOf("/api/");
        return i > 0 ? url.substring(0, i) : url;
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return "";
    }
}
