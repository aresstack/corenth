package de.bund.zrb.ui.settings.provider;

import de.bund.zrb.model.AiProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Statische Factories für die {@link ProviderDef}-Beschreibungen aller AI-Provider.
 *
 * <p>Diese Definitionen werden sowohl vom allgemeinen AI-Tab ({@code AiSettingsPanel})
 * als auch vom Embeddings-Override (RagSettingsPanel via {@link ProviderConfigPanel})
 * genutzt — gleiche Felder, gleiche Schlüssel, gleiches Rendering. Der Aufrufer filtert
 * über {@link Facet}: das Override gibt nur {@code EnumSet.of(EMBEDDINGS)} mit, der
 * Allgemein-Tab {@code EnumSet.allOf(Facet.class)}.</p>
 *
 * <p>Provider-spezifische Spezialknöpfe (Fetch, Browse, Extract, Vendor-Listener)
 * werden <b>nach</b> dem Render-Aufruf von {@code AiSettingsPanel} über
 * {@link ProviderCardRenderer.RenderedCard#getRow(String)} eingehängt — der Renderer
 * bleibt provider-agnostisch.</p>
 */
public final class ProviderDefinitions {

    private ProviderDefinitions() {}

    // -----------------------------------------------------------------
    //  OLLAMA
    // -----------------------------------------------------------------
    public static ProviderDef ollama() {
        return ProviderDef.of(AiProvider.OLLAMA)
                .field(FieldSpec.text("ollama.url", "URL:")
                        .defaultValue("http://localhost:11434/api/chat").build())
                .slot(ModelSlot.of(Facet.CHAT, "Modellname:", "ollama.model")
                        .modelDefault("custom-modell")
                        .sectionLabel("")
                        .modelsFetcher(ProviderDefinitions::ollamaFetchPlan)
                        .connectionTester(ProviderDefinitions::ollamaChatTestPlan).build())
                .field(FieldSpec.text("ollama.keepalive", "Beibehalten für:")
                        .defaultValue("10m").build())
                .slot(ModelSlot.of(Facet.EMBEDDINGS, "Embeddings-Modell:", "ollama.model.embeddings")
                        .sectionLabel("Embeddings & Reranker")
                        .modelTooltip("Ollama-Modell für /api/embeddings (RAG-Indexierung)")
                        .modelsFetcher(ProviderDefinitions::ollamaFetchPlan)
                        .connectionTester(ProviderDefinitions::ollamaEmbedTestPlan).build())
                .slot(ModelSlot.of(Facet.RERANKER, "Reranker-Modell:", "ollama.model.rerank")
                        .sectionLabel("")
                        .modelTooltip("Ollama-Modell für Reranker (Cross-Encoder)")
                        .modelsFetcher(ProviderDefinitions::ollamaFetchPlan)
                        .connectionTester(ProviderDefinitions::ollamaRerankTestPlan).build())
                .slot(ModelSlot.of(Facet.SUMMARIZE, "Summarizer-Modell:", "ollama.model.summarize")
                        .sectionLabel("Summarizer (Auxiliary)")
                        .modelDefault("qwen2.5:0.5b")
                        .modelTooltip("Kleines, CPU-taugliches Ollama-Modell für Kurz-Zusammenfassungen (UML-Knoten, RAG-Titel, Tooltips)")
                        .modelsFetcher(ProviderDefinitions::ollamaFetchPlan)
                        .connectionTester(ProviderDefinitions::ollamaSummarizeTestPlan).build())
                .field(FieldSpec.info("Ollama bietet aktuell keinen nativen TTS-Endpunkt.")
                        .section("Audio")
                        .requiredFacet(Facet.AUDIO).build())
                .field(FieldSpec.info(
                                "Ollama hat keine zustandsbehaftete Responses-API. Konversations-State müsste clientseitig verwaltet werden.")
                        .section("Responses-API")
                        .requiredFacet(Facet.RESPONSES).build())
                // Proxy-Auth- und E2E-Credentials liegen jetzt global im Proxy-Tab,
                // die Aktivierung steuert je KI-Tab eine eigene Checkbox.
                // Sonstige Endpunkt-Pfade (relativ zur Base aus 'URL'). Default = Ollama-API-Spec;
                // Override für abweichende Forks / Proxies.
                .field(FieldSpec.text("ollama.endpoint.models", "GET Models:")
                        .section("Sonstige Endpunkt-Pfade")
                        .defaultValue("/api/tags")
                        .tooltip("Pfad zum Modell-Listen-Endpunkt (relativ zur Ollama-Base-URL). "
                                + "Standard ist /api/tags; nur ändern, wenn ein Fork einen abweichenden Pfad anbietet.")
                        .withResetButton().build())
                .build();
    }

    // -----------------------------------------------------------------
    //  CLOUD (Public)
    // -----------------------------------------------------------------
    public static ProviderDef cloud() {
        return ProviderDef.of(AiProvider.CLOUD)
                .field(FieldSpec.comboFixed("cloud.vendor", "Cloud-Anbieter:",
                                new String[]{"OPENAI", "CLAUDE", "PERPLEXITY", "GROK", "GEMINI"})
                        .defaultValue("OPENAI").build())
                .field(FieldSpec.text("cloud.apikey", "API Key:").build())
                .field(FieldSpec.text("cloud.url", "API URL:").build())
                .slot(ModelSlot.of(Facet.CHAT, "Modell:", "cloud.model")
                        .modelDefault("gpt-4o-mini")
                        .sectionLabel("")
                        .modelsFetcher(ProviderDefinitions::cloudFetchPlan)
                        .connectionTester(ProviderDefinitions::cloudChatTestPlan).build())
                .field(FieldSpec.text("cloud.authHeader", "Auth Header:")
                        .defaultValue("Authorization").build())
                .field(FieldSpec.text("cloud.authPrefix", "Auth Prefix:")
                        .defaultValue("Bearer").build())
                .field(FieldSpec.text("cloud.anthropicVersion", "Anthropic-Version:").build())
                .field(FieldSpec.text("cloud.organization", "Organisation:").build())
                .field(FieldSpec.text("cloud.project", "Projekt:").build())
                // Embeddings & Reranker
                .slot(ModelSlot.of(Facet.EMBEDDINGS, "Embeddings-Modell:", "cloud.model.embeddings")
                        .modelDefault("text-embedding-3-small")
                        .sectionLabel("Embeddings & Reranker")
                        .url("cloud.url.embeddings", "Embeddings URL:", "")
                        .urlTooltip("Vollständige URL des Embeddings-Endpunkts, z. B. https://api.openai.com/v1/embeddings")
                        .modelTooltip("Modell für /v1/embeddings (RAG-Indexierung)")
                        .modelsFetcher(ProviderDefinitions::cloudFetchPlan)
                        .connectionTester(ProviderDefinitions::cloudEmbedTestPlan).build())
                .slot(ModelSlot.of(Facet.RERANKER, "Reranker-Modell:", "cloud.model.rerank")
                        .sectionLabel("")
                        .url("cloud.url.rerank", "Reranker URL:", "")
                        .urlTooltip("Vollständige URL des Reranker-Endpunkts (z. B. https://api.jina.ai/v1/rerank)")
                        .modelTooltip("Modell für Reranking (Cross-Encoder)")
                        .modelsFetcher(ProviderDefinitions::cloudFetchPlan)
                        .connectionTester(ProviderDefinitions::cloudRerankTestPlan).build())
                .slot(ModelSlot.of(Facet.SUMMARIZE, "Summarizer-Modell:", "cloud.model.summarize")
                        .sectionLabel("Summarizer (Auxiliary)")
                        .modelDefault("gpt-4o-mini")
                        .url("cloud.url.summarize", "Summarizer URL:", "")
                        .urlTooltip("Optional: abweichende Chat-URL für den Summarizer. Leer = Chat-URL verwenden.")
                        .modelTooltip("Kleines Cloud-Modell für Kurz-Zusammenfassungen (z. B. gpt-4o-mini)")
                        .modelsFetcher(ProviderDefinitions::cloudFetchPlan)
                        .connectionTester(ProviderDefinitions::cloudSummarizeTestPlan).build())
                // Audio
                .slot(ModelSlot.of(Facet.AUDIO, "Audio-Modell:", "cloud.model.audio")
                        .modelDefault("tts-1")
                        .sectionLabel("Audio")
                        .url("cloud.url.audio", "Audio URL:", "")
                        .urlTooltip("Vollständige URL des Audio/TTS-Endpunkts, z. B. https://api.openai.com/v1/audio/speech")
                        .modelTooltip("TTS-Modell, z. B. tts-1, tts-1-hd").build())
                .field(FieldSpec.info(
                                "Nicht jeder Cloud-Anbieter bietet einen TTS-Endpunkt — siehe Anbieter-Dokumentation.")
                        .requiredFacet(Facet.AUDIO).build())
                // Responses-API
                .field(FieldSpec.info(
                                "Zustandsbehaftete API: Chatnachrichten werden serverseitig als Ressourcen verwaltet. "
                                        + "Nur von wenigen Anbietern unterstützt (z. B. OpenAI).")
                        .section("Responses-API")
                        .requiredFacet(Facet.RESPONSES).build())
                .field(FieldSpec.text("cloud.url.responses", "Responses URL:")
                        .tooltip("URL für POST /v1/responses")
                        .requiredFacet(Facet.RESPONSES).build())
                .field(FieldSpec.text("cloud.url.responseById", "Response-by-ID URL:")
                        .tooltip("URL-Vorlage für GET /v1/responses/{response_id}")
                        .requiredFacet(Facet.RESPONSES).build())
                // Sonstige Endpunkt-Pfade — optionales Override für die Modell-Liste.
                // Leer lassen ⇒ Fetcher leitet automatisch ab (OPENAI/Compatible: {base}/v1/models;
                // GROK/GEMINI: vendor-spezifisch hardcoded; CLAUDE/PERPLEXITY: kein Listen-Endpunkt).
                .field(FieldSpec.text("cloud.url.models", "GET Models URL:")
                        .section("Sonstige Endpunkt-Pfade")
                        .tooltip("Optional: vollständige URL des Modell-Listen-Endpunkts. "
                                + "Leer lassen ⇒ wird aus 'API URL' abgeleitet (OPENAI: …/v1/models). "
                                + "CLAUDE/PERPLEXITY bieten keinen solchen Endpunkt.")
                        .build())
                .build();
    }

    // -----------------------------------------------------------------
    //  PRIVATE_CLOUD  (Compatible / Custom Sub-Modi)
    // -----------------------------------------------------------------
    public static ProviderDef privateCloud() {
        ProviderDef.SubMode compatible = ProviderDef.SubMode.of("OpenAI Compatible", "compatible")
                .field(FieldSpec.info(
                                "<html>Selbstgehosteter Endpunkt mit OpenAI-Standardpfaden. "
                                        + "Die Defaults sind direkt editierbar; der ↺-Button setzt das Feld zurück.</html>")
                        .build())
                .field(FieldSpec.text("openaiCompatible.baseUrl", "Base URL:")
                        .tooltip("Basis-URL ohne Pfad, z. B. https://api.openai.com — die Endpunkte werden daran angehängt.")
                        .defaultValue("https://api.openai.com")
                        .withResetButton().build())
                .field(FieldSpec.text("openaiCompatible.apikey", "API Key:").build())
                .slot(ModelSlot.of(Facet.CHAT, "Chat-Modell:", "openaiCompatible.model")
                        .sectionLabel("")
                        .modelsFetcher(ProviderDefinitions::oaicFetchPlan)
                        .connectionTester(ProviderDefinitions::oaicChatTestPlan)
                        .endpoint("openaiCompatible.endpoint.chat", "POST Chat Completions:", "/v1/chat/completions")
                        .build())
                .slot(ModelSlot.of(Facet.EMBEDDINGS, "Embeddings-Modell:", "openaiCompatible.model.embeddings")
                        .sectionLabel("Embeddings & Reranker")
                        .modelTooltip("Modell für /v1/embeddings (RAG-Indexierung)")
                        .endpoint("openaiCompatible.endpoint.embeddings", "POST Embeddings:", "/v1/embeddings")
                        .modelsFetcher(ProviderDefinitions::oaicFetchPlan)
                        .connectionTester(ProviderDefinitions::oaicEmbedTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.RERANKER, "Reranker-Modell:", "openaiCompatible.model.rerank")
                        .sectionLabel("")
                        .modelTooltip("Modell für /v1/rerank (RAG-Reranking)")
                        .endpoint("openaiCompatible.endpoint.rerank", "POST Rerank:", "/v1/rerank")
                        .modelsFetcher(ProviderDefinitions::oaicFetchPlan)
                        .connectionTester(ProviderDefinitions::oaicRerankTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.SUMMARIZE, "Summarizer-Modell:", "openaiCompatible.model.summarize")
                        .sectionLabel("Summarizer (Auxiliary)")
                        .modelDefault("qwen2.5:0.5b")
                        .modelTooltip("Kleines Modell für Kurz-Zusammenfassungen")
                        .endpoint("openaiCompatible.endpoint.summarize", "POST Summarize (Chat):", "/v1/chat/completions")
                        .modelsFetcher(ProviderDefinitions::oaicFetchPlan)
                        .connectionTester(ProviderDefinitions::oaicSummarizeTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.AUDIO, "Audio-Modell:", "openaiCompatible.model.audio")
                        .sectionLabel("Audio")
                        .modelTooltip("TTS-Modell, z. B. tts-1")
                        .endpoint("openaiCompatible.endpoint.audio", "POST Audio/Speech:", "/v1/audio/speech")
                        .build())
                .field(FieldSpec.info(
                                "Zustandsbehaftete API: Chatnachrichten werden serverseitig als Ressourcen verwaltet. "
                                        + "Wird nur unterstützt, wenn der Backend-Server diese Endpunkte bereitstellt.")
                        .section("Responses-API")
                        .requiredFacet(Facet.RESPONSES).build())
                .field(FieldSpec.text("openaiCompatible.endpoint.responses", "POST Responses:")
                        .defaultValue("/v1/responses")
                        .withResetButton()
                        .requiredFacet(Facet.RESPONSES).build())
                .field(FieldSpec.text("openaiCompatible.endpoint.responseById", "GET Response by ID:")
                        .defaultValue("/v1/responses/{response_id}")
                        .withResetButton()
                        .requiredFacet(Facet.RESPONSES).build())
                .field(FieldSpec.text("openaiCompatible.endpoint.models", "GET Models:")
                        .section("Sonstige Endpunkt-Pfade (relativ zur Base URL)")
                        .defaultValue("/v1/models")
                        .withResetButton().build())
                .build();

        ProviderDef.SubMode custom = ProviderDef.SubMode.of("Custom", "custom")
                .field(FieldSpec.info(
                                "<html>Voll konfigurierbarer Endpunkt: alle Pfade sowie sämtliche HTTP-Header "
                                        + "werden manuell eingegeben. Endpunkt-Defaults entsprechen dem OpenAI-Schema und können per ↺ "
                                        + "wiederhergestellt werden.</html>")
                        .build())
                .field(FieldSpec.text("custom.baseUrl", "Base URL:")
                        .tooltip("Basis-URL ohne Pfad, z. B. https://api.example.com")
                        .withResetButton().build())
                .slot(ModelSlot.of(Facet.CHAT, "Chat-Modell:", "custom.model")
                        .sectionLabel("")
                        .modelsFetcher(ProviderDefinitions::customFetchPlan)
                        .connectionTester(ProviderDefinitions::customChatTestPlan)
                        .endpoint("custom.endpoint.chat", "POST Chat Completions:", "/v1/chat/completions")
                        .build())
                .slot(ModelSlot.of(Facet.EMBEDDINGS, "Embeddings-Modell:", "custom.model.embeddings")
                        .sectionLabel("Embeddings & Reranker")
                        .modelTooltip("Modell für /v1/embeddings (RAG-Indexierung)")
                        .endpoint("custom.endpoint.embeddings", "POST Embeddings:", "/v1/embeddings")
                        .modelsFetcher(ProviderDefinitions::customFetchPlan)
                        .connectionTester(ProviderDefinitions::customEmbedTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.RERANKER, "Reranker-Modell:", "custom.model.rerank")
                        .sectionLabel("")
                        .modelTooltip("Modell für /v1/rerank (RAG-Reranking)")
                        .endpoint("custom.endpoint.rerank", "POST Rerank:", "/v1/rerank")
                        .modelsFetcher(ProviderDefinitions::customFetchPlan)
                        .connectionTester(ProviderDefinitions::customRerankTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.SUMMARIZE, "Summarizer-Modell:", "custom.model.summarize")
                        .sectionLabel("Summarizer (Auxiliary)")
                        .modelDefault("qwen2.5:0.5b")
                        .modelTooltip("Kleines Modell für Kurz-Zusammenfassungen")
                        .endpoint("custom.endpoint.summarize", "POST Summarize (Chat):", "/v1/chat/completions")
                        .modelsFetcher(ProviderDefinitions::customFetchPlan)
                        .connectionTester(ProviderDefinitions::customSummarizeTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.AUDIO, "Audio-Modell:", "custom.model.audio")
                        .sectionLabel("Audio")
                        .modelTooltip("TTS-Modell, z. B. tts-1")
                        .endpoint("custom.endpoint.audio", "POST Audio/Speech:", "/v1/audio/speech")
                        .build())
                .field(FieldSpec.info(
                                "Zustandsbehaftete API: Chatnachrichten werden serverseitig als Ressourcen verwaltet. "
                                        + "Wird nur unterstützt, wenn der Backend-Server diese Endpunkte bereitstellt. "
                                        + "Die zugehörigen Pfade (POST Responses / GET Response by ID) findest du unten in den Endpunkt-Pfaden.")
                        .section("Responses-API")
                        .requiredFacet(Facet.RESPONSES).build())
                .field(FieldSpec.headerTable("custom.header.", "HTTP-Header")
                        .section("HTTP-Header")
                        .withResetButton()
                        .headerDefault("Authorization", "Bearer YOUR_API_KEY")
                        .headerDefault("Content-Type", "application/json")
                        .build())
                .field(FieldSpec.text("custom.endpoint.models", "GET Models:")
                        .section("Endpunkt-Pfade (relativ zur Base URL)")
                        .defaultValue("/v1/models")
                        .withResetButton().build())
                .field(FieldSpec.text("custom.endpoint.responses", "POST Responses:")
                        .defaultValue("/v1/responses")
                        .withResetButton()
                        .requiredFacet(Facet.RESPONSES).build())
                .field(FieldSpec.text("custom.endpoint.responseById", "GET Response by ID:")
                        .defaultValue("/v1/responses/{response_id}")
                        .withResetButton()
                        .requiredFacet(Facet.RESPONSES).build())
                .build();

        return ProviderDef.of(AiProvider.PRIVATE_CLOUD)
                .subModes("privateCloud.mode", "Modus:", compatible, custom)
                .build();
    }

    // -----------------------------------------------------------------
    //  LOCAL_AI
    // -----------------------------------------------------------------
    public static ProviderDef localAi() {
        return ProviderDef.of(AiProvider.LOCAL_AI)
                .field(FieldSpec.info("<html><b>LocalAI</b> — OpenAI-kompatibler lokaler Server. "
                        + "Chat-Komplettierungen sind in MainframeMate noch nicht implementiert; "
                        + "die Felder werden aber bereits persistiert.</html>").build())
                .slot(ModelSlot.of(Facet.CHAT, "Chat-Modell:", "localai.model")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("")
                        .url("localai.url", "Chat URL:", "")
                        .urlTooltip("Vollständige Chat-URL, z. B. http://localhost:8080/v1/chat/completions")
                        .connectionTester(ProviderDefinitions::localAiChatTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.EMBEDDINGS, "Embeddings-Modell:", "localai.model.embeddings")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("Embeddings & Reranker")
                        .url("localai.url.embeddings", "Embeddings URL:", "")
                        .urlTooltip("Vollständige Embeddings-URL, z. B. http://localhost:8080/v1/embeddings")
                        .connectionTester(ProviderDefinitions::localAiEmbedTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.RERANKER, "Reranker-Modell:", "localai.model.rerank")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("")
                        .url("localai.url.rerank", "Reranker URL:", "")
                        .urlTooltip("Vollständige Reranker-URL")
                        .connectionTester(ProviderDefinitions::localAiRerankTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.SUMMARIZE, "Summarizer-Modell:", "localai.model.summarize")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("Summarizer (Auxiliary)")
                        .modelDefault("qwen2.5:0.5b")
                        .url("localai.url.summarize", "Summarizer URL:", "")
                        .urlTooltip("Vollständige Chat-URL für den Summarizer; leer = Chat-URL verwenden.")
                        .modelTooltip("Kleines, CPU-taugliches Modell für Kurz-Zusammenfassungen")
                        .connectionTester(ProviderDefinitions::localAiSummarizeTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.AUDIO, "Audio-Modell:", "localai.model.audio")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("Audio")
                        .url("localai.url.audio", "Audio URL:", "")
                        .urlTooltip("Vollständige TTS-URL, z. B. http://localhost:8080/v1/audio/speech")
                        .modelTooltip("Audio-/TTS-Modell, z. B. tts-1")
                        .build())
                .field(FieldSpec.info("LocalAI bietet aktuell keine OpenAI-kompatible Responses-API (stateful). "
                                + "Konversations-State müsste clientseitig verwaltet werden.")
                        .section("Responses-API")
                        .requiredFacet(Facet.RESPONSES).build())
                .build();
    }

    // -----------------------------------------------------------------
    //  LLAMA_CPP_SERVER
    // -----------------------------------------------------------------
    public static ProviderDef llamaCpp() {
        return ProviderDef.of(AiProvider.LLAMA_CPP_SERVER)
                .field(FieldSpec.checkbox("llama.streaming", "Streaming aktiviert")
                        .defaultValue("true").build())
                .field(FieldSpec.checkbox("llama.enabled", "llama.cpp Server beim Start starten")
                        .defaultValue("false").build())
                .field(FieldSpec.text("llama.binary", "Binary-Pfad:")
                        .defaultValue("C:/llamacpp/llama-server").build())
                .slot(ModelSlot.of(Facet.CHAT, "Modellpfad (.gguf):", "llama.model")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .modelDefault("models/mistral.gguf")
                        .sectionLabel("")
                        .connectionTester(ProviderDefinitions::llamaChatTestPlan).build())
                .field(FieldSpec.intSpinner("llama.port", "Port:", 1024, 65535, 1)
                        .defaultValue("8080").build())
                .field(FieldSpec.intSpinner("llama.threads", "Threads:", 1, 64, 1)
                        .defaultValue("4").build())
                .field(FieldSpec.intSpinner("llama.context", "Kontextgröße:", 512, 8192, 64)
                        .defaultValue("2048").build())
                .field(FieldSpec.text("llama.temp", "Temperatur:").defaultValue("0.7").build())
                .field(FieldSpec.info("<html><small>Nutzen denselben Server (Port s. o.) — separate Modelldateien.</small></html>")
                        .section("Embeddings & Reranker")
                        .build())
                .slot(ModelSlot.of(Facet.EMBEDDINGS, "Embeddings-Modellpfad:", "llama.model.embeddings")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("")
                        .modelTooltip("Pfad zur Embedding-Modelldatei (.gguf), z. B. models/nomic-embed.gguf")
                        .connectionTester(ProviderDefinitions::llamaEmbedTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.RERANKER, "Reranker-Modellpfad:", "llama.model.rerank")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("")
                        .modelTooltip("Pfad zur Reranker-Modelldatei (.gguf)")
                        .connectionTester(ProviderDefinitions::llamaRerankTestPlan)
                        .build())
                .slot(ModelSlot.of(Facet.SUMMARIZE, "Summarizer-Modellpfad:", "llama.model.summarize")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("Summarizer (Auxiliary)")
                        .modelTooltip("Pfad zur Summarizer-Modelldatei (.gguf), z. B. ein kleines Qwen/Phi-Mini")
                        .connectionTester(ProviderDefinitions::llamaSummarizeTestPlan)
                        .build())
                .field(FieldSpec.info("llama.cpp Server unterstützt keinen nativen TTS-Endpunkt.")
                        .section("Audio")
                        .requiredFacet(Facet.AUDIO).build())
                .field(FieldSpec.info("llama.cpp Server hat keine zustandsbehaftete Responses-API. "
                                + "Konversations-State müsste clientseitig verwaltet werden.")
                        .section("Responses-API")
                        .requiredFacet(Facet.RESPONSES).build())
                .build();
    }

    // -----------------------------------------------------------------
    //  ONNX_RUNTIME
    // -----------------------------------------------------------------
    public static ProviderDef onnx() {
        return ProviderDef.of(AiProvider.ONNX_RUNTIME)
                .field(FieldSpec.info("<html><b>ONNX Runtime</b> – lokale LLM-Inferenz mit Phi-3/Phi-4 Modellen.<br>"
                        + "Modell als ONNX-Verzeichnis von Hugging Face herunterladen.</html>").build())
                .slot(ModelSlot.of(Facet.CHAT, "Modellpfad:", "onnx.model.path")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("")
                        .modelTooltip("Pfad zum ONNX-Modellverzeichnis (z.B. C:\\models\\Phi-3-mini-4k-instruct-onnx)")
                        .build())
                .field(FieldSpec.comboFixed("onnx.execution.provider", "Execution Provider:",
                                new String[]{"directml", "cpu"})
                        .defaultValue("directml")
                        .tooltip("DirectML = GPU (empfohlen). CPU = Fallback ohne GPU-Beschleunigung.")
                        .build())
                .field(FieldSpec.intSpinner("onnx.max.tokens", "Max Tokens:", 1, 4096, 64)
                        .defaultValue("256").build())
                .field(FieldSpec.text("onnx.temperature", "Temperatur:").defaultValue("0.7").build())
                .field(FieldSpec.text("onnx.top.p", "Top-P:").defaultValue("0.9").build())
                .field(FieldSpec.intSpinner("onnx.top.k", "Top-K:", 0, 1000, 1)
                        .defaultValue("40").build())
                .field(FieldSpec.info("<html><small>Eigene ONNX-Modellverzeichnisse für Embeddings/Reranker.</small></html>")
                        .section("Embeddings & Reranker").build())
                .slot(ModelSlot.of(Facet.EMBEDDINGS, "Embeddings-Modellpfad:", "onnx.model.embeddings.path")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("")
                        .modelTooltip("Pfad zum ONNX-Embedding-Modellverzeichnis")
                        .build())
                .slot(ModelSlot.of(Facet.RERANKER, "Reranker-Modellpfad:", "onnx.model.rerank.path")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("")
                        .modelTooltip("Pfad zum ONNX-Reranker-Modellverzeichnis")
                        .build())
                .slot(ModelSlot.of(Facet.SUMMARIZE, "Summarizer-Modellpfad:", "onnx.model.summarize.path")
                        .modelType(ModelSlot.ModelType.TEXT)
                        .sectionLabel("Summarizer (Auxiliary)")
                        .modelTooltip("Pfad zu einem kleinen ONNX-LLM für Kurz-Zusammenfassungen")
                        .build())
                .field(FieldSpec.info(
                                "ONNX Runtime führt aktuell nur LLM-Inferenz aus; ein nativer TTS-Endpunkt ist nicht vorgesehen.")
                        .section("Audio")
                        .requiredFacet(Facet.AUDIO).build())
                .field(FieldSpec.info(
                                "Lokale ONNX-Inferenz ist zustandslos. Eine Responses-API (stateful) wird nicht bereitgestellt; "
                                        + "Konversations-State müsste clientseitig verwaltet werden.")
                        .section("Responses-API")
                        .requiredFacet(Facet.RESPONSES).build())
                .build();
    }

    // -----------------------------------------------------------------
    //  ModelsFetchPlan-Helfer (vom 🔄-Button via ModelSlot.modelsFetcher aufgerufen)
    // -----------------------------------------------------------------

    /** Ollama: GET {base}{endpoint.models} (kein Auth). Default-Pfad: /api/tags. */
    static ModelsFetchPlan ollamaFetchPlan(Map<String, String> cfg) {
        String url = cfg.getOrDefault("ollama.url", "").trim();
        if (url.isEmpty()) return ModelsFetchPlan.error("⚠️ Bitte zuerst die Ollama-URL setzen");
        if (url.contains("/api/")) url = url.substring(0, url.indexOf("/api/"));
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        String path = cfg.getOrDefault("ollama.endpoint.models", "/api/tags").trim();
        if (path.isEmpty()) path = "/api/tags";
        if (!path.startsWith("/")) path = "/" + path;
        return new ModelsFetchPlan(url + path, null);
    }

    /** Cloud-Anbieter: vendor-abhängige Models-URL (OPENAI/GROK/GEMINI; CLAUDE/PERPLEXITY ohne). */
    static ModelsFetchPlan cloudFetchPlan(Map<String, String> cfg) {
        String vendor = Objects.toString(cfg.get("cloud.vendor"), "OPENAI");
        String apiKey = Objects.toString(cfg.get("cloud.apikey"), "").trim();
        String apiUrl = Objects.toString(cfg.get("cloud.url"), "").trim();
        String override = Objects.toString(cfg.get("cloud.url.models"), "").trim();
        String authHeader = cfg.getOrDefault("cloud.authHeader", "Authorization").trim();
        String authPrefix = cfg.getOrDefault("cloud.authPrefix", "Bearer").trim();
        Map<String, String> headers = new LinkedHashMap<String, String>();

        // Explizites Override hat Vorrang vor jeglicher Ableitung.
        if (!override.isEmpty()) {
            if (!apiKey.isEmpty()) {
                String hn = authHeader.isEmpty() ? "Authorization" : authHeader;
                String hv = (authPrefix.isEmpty() ? "Bearer" : authPrefix) + " " + apiKey;
                // GEMINI braucht den Key als Query-Parameter, nicht im Header — also nur setzen,
                // wenn die Override-URL nicht selbst schon einen ?key=…-Parameter mitbringt.
                if (!override.contains("?key=") && !override.contains("&key=")) headers.put(hn, hv);
            }
            return new ModelsFetchPlan(override, headers);
        }

        String url;
        switch (vendor) {
            case "OPENAI":
                url = deriveModelsUrl(apiUrl, "/v1/models");
                if (!apiKey.isEmpty()) headers.put(authHeader.isEmpty() ? "Authorization" : authHeader,
                        (authPrefix.isEmpty() ? "Bearer" : authPrefix) + " " + apiKey);
                return new ModelsFetchPlan(url, headers);
            case "GROK":
                url = "https://api.x.ai/v1/models";
                if (!apiKey.isEmpty()) headers.put("Authorization", "Bearer " + apiKey);
                return new ModelsFetchPlan(url, headers);
            case "GEMINI":
                if (apiKey.isEmpty()) return ModelsFetchPlan.error("⚠️ API-Key erforderlich");
                return new ModelsFetchPlan("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey, headers);
            case "CLAUDE":
            case "PERPLEXITY":
                return ModelsFetchPlan.error("ℹ️ Kein Modell-Endpunkt für " + vendor
                        + " — manuell unter 'Sonstige Endpunkt-Pfade' eintragen, falls bekannt");
            default:
                url = deriveModelsUrl(apiUrl, "/v1/models");
                if (!apiKey.isEmpty()) headers.put("Authorization", "Bearer " + apiKey);
                return new ModelsFetchPlan(url, headers);
        }
    }

    /** OpenAI-Compatible (Private Cloud, compatible sub-mode): {base}{endpoint.models}. */
    static ModelsFetchPlan oaicFetchPlan(Map<String, String> cfg) {
        String base = Objects.toString(cfg.get("openaiCompatible.baseUrl"), "").trim();
        if (base.isEmpty()) return ModelsFetchPlan.error("⚠️ Bitte zuerst die Base URL setzen");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String modelsPath = Objects.toString(cfg.get("openaiCompatible.endpoint.models"), "").trim();
        if (modelsPath.isEmpty()) modelsPath = "/v1/models";
        if (!modelsPath.startsWith("/")) modelsPath = "/" + modelsPath;
        Map<String, String> headers = new LinkedHashMap<String, String>();
        String apiKey = Objects.toString(cfg.get("openaiCompatible.apikey"), "").trim();
        if (!apiKey.isEmpty()) headers.put("Authorization", "Bearer " + apiKey);
        return new ModelsFetchPlan(base + modelsPath, headers);
    }

    /** Private Cloud Custom-Modus: {base}{endpoint.models} mit allen Custom-Headern. */
    static ModelsFetchPlan customFetchPlan(Map<String, String> cfg) {
        String base = Objects.toString(cfg.get("custom.baseUrl"), "").trim();
        if (base.isEmpty()) return ModelsFetchPlan.error("⚠️ Bitte zuerst die Base URL setzen");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String modelsPath = Objects.toString(cfg.get("custom.endpoint.models"), "").trim();
        if (modelsPath.isEmpty()) modelsPath = "/v1/models";
        if (!modelsPath.startsWith("/")) modelsPath = "/" + modelsPath;
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : cfg.entrySet()) {
            String k = e.getKey();
            if (k != null && k.startsWith("custom.header.")) {
                String name = k.substring("custom.header.".length());
                if (!name.isEmpty() && e.getValue() != null) headers.put(name, e.getValue());
            }
        }
        return new ModelsFetchPlan(base + modelsPath, headers);
    }

    /** Leitet die Modell-Auflistungs-URL aus einer API-URL ab (analog zu AiSettingsPanel.deriveModelsUrl). */
    private static String deriveModelsUrl(String apiUrl, String modelsPath) {
        if (apiUrl == null || apiUrl.isEmpty()) return modelsPath;
        if (apiUrl.contains("/v1/")) return apiUrl.substring(0, apiUrl.indexOf("/v1/")) + modelsPath;
        if (apiUrl.contains("/v2/")) return apiUrl.substring(0, apiUrl.indexOf("/v2/")) + modelsPath;
        int lastSlash = apiUrl.lastIndexOf('/');
        return lastSlash > 8 ? apiUrl.substring(0, lastSlash) + modelsPath : apiUrl + modelsPath;
    }

    // -----------------------------------------------------------------
    //  ConnectionTestPlan-Helfer (vom 🧪-Button via ModelSlot.connectionTester aufgerufen)
    // -----------------------------------------------------------------

    /**
     * Öffentlicher Dispatcher für hand-verdrahtete UIs (z. B. AiSettingsPanel):
     * liefert für einen Provider+Facet den passenden Test-Plan aus dem aktuellen
     * Feld-Snapshot. Liefert {@code null}, falls keine Test-Variante existiert.
     */
    public static ConnectionTestPlan testPlanFor(AiProvider provider, Facet facet, Map<String, String> cfg) {
        if (provider == null || facet == null) return null;
        Map<String, String> m = cfg != null ? cfg : new java.util.HashMap<String, String>();
        switch (provider) {
            case OLLAMA:
                if (facet == Facet.CHAT)       return ollamaChatTestPlan(m);
                if (facet == Facet.EMBEDDINGS) return ollamaEmbedTestPlan(m);
                if (facet == Facet.RERANKER)   return ollamaRerankTestPlan(m);
                if (facet == Facet.SUMMARIZE)  return ollamaSummarizeTestPlan(m);
                return null;
            case CLOUD:
                if (facet == Facet.CHAT)       return cloudChatTestPlan(m);
                if (facet == Facet.EMBEDDINGS) return cloudEmbedTestPlan(m);
                if (facet == Facet.RERANKER)   return cloudRerankTestPlan(m);
                if (facet == Facet.SUMMARIZE)  return cloudSummarizeTestPlan(m);
                return null;
            case PRIVATE_CLOUD: {
                String mode = m.getOrDefault("privateCloud.mode", "compatible");
                boolean custom = "custom".equalsIgnoreCase(mode);
                if (facet == Facet.CHAT)       return custom ? customChatTestPlan(m)   : oaicChatTestPlan(m);
                if (facet == Facet.EMBEDDINGS) return custom ? customEmbedTestPlan(m)  : oaicEmbedTestPlan(m);
                if (facet == Facet.RERANKER)   return custom ? customRerankTestPlan(m) : oaicRerankTestPlan(m);
                if (facet == Facet.SUMMARIZE)  return custom ? customSummarizeTestPlan(m) : oaicSummarizeTestPlan(m);
                return null;
            }
            case LOCAL_AI:
                if (facet == Facet.CHAT)       return localAiChatTestPlan(m);
                if (facet == Facet.EMBEDDINGS) return localAiEmbedTestPlan(m);
                if (facet == Facet.RERANKER)   return localAiRerankTestPlan(m);
                if (facet == Facet.SUMMARIZE)  return localAiSummarizeTestPlan(m);
                return null;
            case LLAMA_CPP_SERVER:
                if (facet == Facet.CHAT)       return llamaChatTestPlan(m);
                if (facet == Facet.EMBEDDINGS) return llamaEmbedTestPlan(m);
                if (facet == Facet.RERANKER)   return llamaRerankTestPlan(m);
                if (facet == Facet.SUMMARIZE)  return llamaSummarizeTestPlan(m);
                return null;
            case ONNX_RUNTIME:
            case DISABLED:
            default:
                return null;
        }
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private static String stripApiPath(String url) {
        if (url == null) return "";
        int i = url.indexOf("/api/");
        if (i > 0) return url.substring(0, i);
        return url;
    }

    private static String joinBase(String base, String path) {
        if (base == null) base = "";
        if (path == null) path = "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!path.isEmpty() && !path.startsWith("/")) path = "/" + path;
        return base + path;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── Ollama ─────────────────────────────────────────────────────────
    static ConnectionTestPlan ollamaChatTestPlan(Map<String, String> cfg) {
        String base = stripApiPath(cfg.getOrDefault("ollama.url", "http://localhost:11434"));
        String model = firstNonEmpty(cfg.get("ollama.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Chat-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}]"
                + ",\"stream\":false,\"options\":{\"num_predict\":1}}";
        return ConnectionTestPlan.postJson(base + "/api/chat", null, body);
    }

    static ConnectionTestPlan ollamaEmbedTestPlan(Map<String, String> cfg) {
        String base = stripApiPath(cfg.getOrDefault("ollama.url", "http://localhost:11434"));
        String model = firstNonEmpty(cfg.get("ollama.model.embeddings"), cfg.get("ollama.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Embeddings-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model) + "\",\"input\":\"ping\"}";
        return ConnectionTestPlan.postJson(base + "/api/embed", null, body);
    }

    /** Ollama-Reranker: derselbe /api/embed-Endpunkt, anderes Modell. */
    static ConnectionTestPlan ollamaRerankTestPlan(Map<String, String> cfg) {
        String base = stripApiPath(cfg.getOrDefault("ollama.url", "http://localhost:11434"));
        String model = firstNonEmpty(cfg.get("ollama.model.rerank"), cfg.get("ollama.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Reranker-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model) + "\",\"input\":\"ping\"}";
        return ConnectionTestPlan.postJson(base + "/api/embed", null, body);
    }

    // ── Cloud (Public) ─────────────────────────────────────────────────
    private static Map<String, String> cloudAuthHeaders(Map<String, String> cfg) {
        Map<String, String> h = new LinkedHashMap<String, String>();
        String apiKey = firstNonEmpty(cfg.get("cloud.apikey"));
        if (apiKey.isEmpty()) return h;
        String name = firstNonEmpty(cfg.get("cloud.authHeader"));
        if (name.isEmpty()) name = "Authorization";
        String prefix = firstNonEmpty(cfg.get("cloud.authPrefix"));
        h.put(name, (prefix.isEmpty() ? apiKey : prefix + " " + apiKey));
        String anthropic = firstNonEmpty(cfg.get("cloud.anthropicVersion"));
        if (!anthropic.isEmpty()) h.put("anthropic-version", anthropic);
        String org = firstNonEmpty(cfg.get("cloud.organization"));
        if (!org.isEmpty()) h.put("OpenAI-Organization", org);
        String proj = firstNonEmpty(cfg.get("cloud.project"));
        if (!proj.isEmpty()) h.put("OpenAI-Project", proj);
        return h;
    }

    static ConnectionTestPlan cloudChatTestPlan(Map<String, String> cfg) {
        String url = firstNonEmpty(cfg.get("cloud.url"));
        if (url.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte API-URL setzen");
        String model = firstNonEmpty(cfg.get("cloud.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Modell setzen");
        String vendor = Objects.toString(cfg.get("cloud.vendor"), "OPENAI");
        Map<String, String> h = cloudAuthHeaders(cfg);
        // Anthropic verwendet eine andere Body-Form.
        if ("CLAUDE".equalsIgnoreCase(vendor)) {
            String body = "{\"model\":\"" + jsonEscape(model)
                    + "\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}]}";
            return ConnectionTestPlan.postJson(url, h, body);
        }
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false}";
        return ConnectionTestPlan.postJson(url, h, body);
    }

    static ConnectionTestPlan cloudEmbedTestPlan(Map<String, String> cfg) {
        String url = firstNonEmpty(cfg.get("cloud.url.embeddings"));
        if (url.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Embeddings-URL setzen");
        String model = firstNonEmpty(cfg.get("cloud.model.embeddings"), cfg.get("cloud.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Embeddings-Modell setzen");
        Map<String, String> h = cloudAuthHeaders(cfg);
        String body = "{\"model\":\"" + jsonEscape(model) + "\",\"input\":\"ping\"}";
        return ConnectionTestPlan.postJson(url, h, body);
    }

    static ConnectionTestPlan cloudRerankTestPlan(Map<String, String> cfg) {
        String url = firstNonEmpty(cfg.get("cloud.url.rerank"));
        if (url.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Reranker-URL setzen");
        String model = firstNonEmpty(cfg.get("cloud.model.rerank"), cfg.get("cloud.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Reranker-Modell setzen");
        Map<String, String> h = cloudAuthHeaders(cfg);
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"query\":\"ping\",\"documents\":[\"hello\",\"world\"],\"top_n\":1}";
        return ConnectionTestPlan.postJson(url, h, body);
    }

    // ── Private Cloud / OpenAI-Compatible ──────────────────────────────
    private static Map<String, String> bearer(String apiKey) {
        Map<String, String> h = new LinkedHashMap<String, String>();
        if (apiKey != null && !apiKey.isEmpty()) h.put("Authorization", "Bearer " + apiKey);
        return h;
    }

    static ConnectionTestPlan oaicChatTestPlan(Map<String, String> cfg) {
        String base = firstNonEmpty(cfg.get("openaiCompatible.baseUrl"));
        if (base.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Base URL setzen");
        String model = firstNonEmpty(cfg.get("openaiCompatible.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Chat-Modell setzen");
        String ep = firstNonEmpty(cfg.get("openaiCompatible.endpoint.chat"));
        if (ep.isEmpty()) ep = "/v1/chat/completions";
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false}";
        return ConnectionTestPlan.postJson(joinBase(base, ep),
                bearer(firstNonEmpty(cfg.get("openaiCompatible.apikey"))), body);
    }

    static ConnectionTestPlan oaicEmbedTestPlan(Map<String, String> cfg) {
        String base = firstNonEmpty(cfg.get("openaiCompatible.baseUrl"));
        if (base.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Base URL setzen");
        String model = firstNonEmpty(cfg.get("openaiCompatible.model.embeddings"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Embeddings-Modell setzen");
        String ep = firstNonEmpty(cfg.get("openaiCompatible.endpoint.embeddings"));
        if (ep.isEmpty()) ep = "/v1/embeddings";
        String body = "{\"model\":\"" + jsonEscape(model) + "\",\"input\":\"ping\"}";
        return ConnectionTestPlan.postJson(joinBase(base, ep),
                bearer(firstNonEmpty(cfg.get("openaiCompatible.apikey"))), body);
    }

    static ConnectionTestPlan oaicRerankTestPlan(Map<String, String> cfg) {
        String base = firstNonEmpty(cfg.get("openaiCompatible.baseUrl"));
        if (base.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Base URL setzen");
        String model = firstNonEmpty(cfg.get("openaiCompatible.model.rerank"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Reranker-Modell setzen");
        String ep = firstNonEmpty(cfg.get("openaiCompatible.endpoint.rerank"));
        if (ep.isEmpty()) ep = "/v1/rerank";
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"query\":\"ping\",\"documents\":[\"hello\",\"world\"],\"top_n\":1}";
        return ConnectionTestPlan.postJson(joinBase(base, ep),
                bearer(firstNonEmpty(cfg.get("openaiCompatible.apikey"))), body);
    }

    // ── Private Cloud / Custom (header table) ──────────────────────────
    private static Map<String, String> customHeaders(Map<String, String> cfg) {
        Map<String, String> h = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : cfg.entrySet()) {
            String k = e.getKey();
            if (k != null && k.startsWith("custom.header.")) {
                String name = k.substring("custom.header.".length());
                if (!name.isEmpty() && e.getValue() != null) h.put(name, e.getValue());
            }
        }
        return h;
    }

    static ConnectionTestPlan customChatTestPlan(Map<String, String> cfg) {
        String base = firstNonEmpty(cfg.get("custom.baseUrl"));
        if (base.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Base URL setzen");
        String model = firstNonEmpty(cfg.get("custom.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Chat-Modell setzen");
        String ep = firstNonEmpty(cfg.get("custom.endpoint.chat"));
        if (ep.isEmpty()) ep = "/v1/chat/completions";
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false}";
        return ConnectionTestPlan.postJson(joinBase(base, ep), customHeaders(cfg), body);
    }

    static ConnectionTestPlan customEmbedTestPlan(Map<String, String> cfg) {
        String base = firstNonEmpty(cfg.get("custom.baseUrl"));
        if (base.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Base URL setzen");
        String model = firstNonEmpty(cfg.get("custom.model.embeddings"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Embeddings-Modell setzen");
        String ep = firstNonEmpty(cfg.get("custom.endpoint.embeddings"));
        if (ep.isEmpty()) ep = "/v1/embeddings";
        String body = "{\"model\":\"" + jsonEscape(model) + "\",\"input\":\"ping\"}";
        return ConnectionTestPlan.postJson(joinBase(base, ep), customHeaders(cfg), body);
    }

    static ConnectionTestPlan customRerankTestPlan(Map<String, String> cfg) {
        String base = firstNonEmpty(cfg.get("custom.baseUrl"));
        if (base.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Base URL setzen");
        String model = firstNonEmpty(cfg.get("custom.model.rerank"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Reranker-Modell setzen");
        String ep = firstNonEmpty(cfg.get("custom.endpoint.rerank"));
        if (ep.isEmpty()) ep = "/v1/rerank";
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"query\":\"ping\",\"documents\":[\"hello\",\"world\"],\"top_n\":1}";
        return ConnectionTestPlan.postJson(joinBase(base, ep), customHeaders(cfg), body);
    }

    // ── LocalAI ────────────────────────────────────────────────────────
    static ConnectionTestPlan localAiChatTestPlan(Map<String, String> cfg) {
        String url = firstNonEmpty(cfg.get("localai.url"));
        if (url.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Chat-URL setzen");
        String model = firstNonEmpty(cfg.get("localai.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Chat-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false}";
        return ConnectionTestPlan.postJson(url, null, body);
    }

    static ConnectionTestPlan localAiEmbedTestPlan(Map<String, String> cfg) {
        String url = firstNonEmpty(cfg.get("localai.url.embeddings"));
        if (url.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Embeddings-URL setzen");
        String model = firstNonEmpty(cfg.get("localai.model.embeddings"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Embeddings-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model) + "\",\"input\":\"ping\"}";
        return ConnectionTestPlan.postJson(url, null, body);
    }

    static ConnectionTestPlan localAiRerankTestPlan(Map<String, String> cfg) {
        String url = firstNonEmpty(cfg.get("localai.url.rerank"));
        if (url.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Reranker-URL setzen");
        String model = firstNonEmpty(cfg.get("localai.model.rerank"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Reranker-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"query\":\"ping\",\"documents\":[\"hello\",\"world\"],\"top_n\":1}";
        return ConnectionTestPlan.postJson(url, null, body);
    }

    // ── llama.cpp Server ───────────────────────────────────────────────
    private static int llamaPort(Map<String, String> cfg) {
        try { return Integer.parseInt(cfg.getOrDefault("llama.port", "8080").trim()); }
        catch (NumberFormatException e) { return 8080; }
    }

    static ConnectionTestPlan llamaChatTestPlan(Map<String, String> cfg) {
        int port = llamaPort(cfg);
        // /health ist eine billige Reachability-Prüfung.
        return ConnectionTestPlan.get("http://localhost:" + port + "/health", null);
    }

    static ConnectionTestPlan llamaEmbedTestPlan(Map<String, String> cfg) {
        int port = llamaPort(cfg);
        String body = "{\"content\":\"ping\"}";
        return ConnectionTestPlan.postJson("http://localhost:" + port + "/embedding", null, body);
    }

    static ConnectionTestPlan llamaRerankTestPlan(Map<String, String> cfg) {
        int port = llamaPort(cfg);
        String body = "{\"query\":\"ping\",\"documents\":[\"hello\",\"world\"]}";
        return ConnectionTestPlan.postJson("http://localhost:" + port + "/v1/rerank", null, body);
    }

    // ── Summarizer (Auxiliary Mini-LLM) ────────────────────────────────
    // Nutzt für jeden Provider denselben Wire-Type wie Chat, aber mit dem
    // *.model.summarize-Schlüssel und (falls vorhanden) einer eigenen URL.

    static ConnectionTestPlan ollamaSummarizeTestPlan(Map<String, String> cfg) {
        String base = stripApiPath(cfg.getOrDefault("ollama.url", "http://localhost:11434"));
        String model = firstNonEmpty(cfg.get("ollama.model.summarize"), cfg.get("ollama.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Summarizer-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}]"
                + ",\"stream\":false,\"options\":{\"num_predict\":1}}";
        return ConnectionTestPlan.postJson(base + "/api/chat", null, body);
    }

    static ConnectionTestPlan cloudSummarizeTestPlan(Map<String, String> cfg) {
        String url = firstNonEmpty(cfg.get("cloud.url.summarize"), cfg.get("cloud.url"));
        if (url.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte API-URL setzen");
        String model = firstNonEmpty(cfg.get("cloud.model.summarize"), cfg.get("cloud.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Summarizer-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false}";
        return ConnectionTestPlan.postJson(url, cloudAuthHeaders(cfg), body);
    }

    static ConnectionTestPlan oaicSummarizeTestPlan(Map<String, String> cfg) {
        String base = firstNonEmpty(cfg.get("openaiCompatible.baseUrl"));
        if (base.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Base URL setzen");
        String model = firstNonEmpty(cfg.get("openaiCompatible.model.summarize"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Summarizer-Modell setzen");
        String ep = firstNonEmpty(cfg.get("openaiCompatible.endpoint.summarize"),
                cfg.get("openaiCompatible.endpoint.chat"));
        if (ep.isEmpty()) ep = "/v1/chat/completions";
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false}";
        return ConnectionTestPlan.postJson(joinBase(base, ep),
                bearer(firstNonEmpty(cfg.get("openaiCompatible.apikey"))), body);
    }

    static ConnectionTestPlan customSummarizeTestPlan(Map<String, String> cfg) {
        String base = firstNonEmpty(cfg.get("custom.baseUrl"));
        if (base.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Base URL setzen");
        String model = firstNonEmpty(cfg.get("custom.model.summarize"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Summarizer-Modell setzen");
        String ep = firstNonEmpty(cfg.get("custom.endpoint.summarize"),
                cfg.get("custom.endpoint.chat"));
        if (ep.isEmpty()) ep = "/v1/chat/completions";
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false}";
        return ConnectionTestPlan.postJson(joinBase(base, ep), customHeaders(cfg), body);
    }

    static ConnectionTestPlan localAiSummarizeTestPlan(Map<String, String> cfg) {
        String url = firstNonEmpty(cfg.get("localai.url.summarize"), cfg.get("localai.url"));
        if (url.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Chat-URL setzen");
        String model = firstNonEmpty(cfg.get("localai.model.summarize"), cfg.get("localai.model"));
        if (model.isEmpty()) return ConnectionTestPlan.error("⚠️ Bitte Summarizer-Modell setzen");
        String body = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false}";
        return ConnectionTestPlan.postJson(url, null, body);
    }

    static ConnectionTestPlan llamaSummarizeTestPlan(Map<String, String> cfg) {
        int port = llamaPort(cfg);
        // Summarizer nutzt OpenAI-kompatible Chat-Route am selben llama.cpp-Server.
        String model = firstNonEmpty(cfg.get("llama.model.summarize"), cfg.get("llama.model"));
        String modelField = model.isEmpty() ? "" : ",\"model\":\"" + jsonEscape(model) + "\"";
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"stream\":false"
                + modelField + "}";
        return ConnectionTestPlan.postJson("http://localhost:" + port + "/v1/chat/completions", null, body);
    }
}

