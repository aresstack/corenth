package de.bund.zrb.ui.settings.provider;

/**
 * Funktionsaspekt eines AI-Providers.
 *
 * <p>Wird genutzt, um per Facet auszuwählen, welche Felder ein
 * {@link ProviderConfigPanel} rendert. Eine Provider-Konfiguration besteht aus
 * <i>immer benötigten</i> Feldern (URL-Basis, Credentials, Auth, Ports, …) sowie
 * <i>facet-spezifischen</i> Feldern (Modell, ggf. eigene URL bzw. Endpoint-Pfad).</p>
 *
 * <p>Beispiel: Der Embedding-Tab-Override verwendet ausschließlich
 * {@link #EMBEDDINGS}; der allgemeine AI-Tab nutzt alle Facets gleichzeitig.</p>
 */
public enum Facet {
    /** Chat-Completion (z.&nbsp;B. {@code /v1/chat/completions}). */
    CHAT,
    /** Vektor-Embeddings (z.&nbsp;B. {@code /v1/embeddings}). */
    EMBEDDINGS,
    /** Reranking (z.&nbsp;B. {@code /v1/rerank}). */
    RERANKER,
    /** Audio/TTS (z.&nbsp;B. {@code /v1/audio/speech}). */
    AUDIO,
    /** Stateful Responses-API (z.&nbsp;B. {@code /v1/responses}). */
    RESPONSES,
    /**
     * Kleiner Auxiliary-LLM für Kurz-Zusammenfassungen (siehe
     * {@code SummarizerService}). Nutzt denselben Wire-Type wie {@link #CHAT}
     * (typischerweise {@code /api/chat} oder {@code /v1/chat/completions}),
     * aber mit eigenem (typischerweise kleinerem) Modell.
     */
    SUMMARIZE
}

