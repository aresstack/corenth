package de.bund.zrb.rag.infrastructure;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.rag.config.RerankerSettings;
import de.bund.zrb.rag.port.RerankerClient;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-based reranker client compatible with common reranker APIs:
 * <ul>
 *   <li><b>TEI</b> (HuggingFace Text Embeddings Inference) — {@code /rerank}</li>
 *   <li><b>Jina Reranker API</b> — {@code /v1/rerank}</li>
 *   <li><b>Cohere Rerank</b> — {@code /v1/rerank}</li>
 *   <li>Any OpenAI-compatible rerank endpoint</li>
 * </ul>
 *
 * <h3>Request format (JSON):</h3>
 * <pre>{@code
 * {
 *   "model": "BAAI/bge-reranker-v2-m3",
 *   "query": "What is deep learning?",
 *   "documents": ["passage 1", "passage 2", ...],
 *   "top_n": 5
 * }
 * }</pre>
 *
 * <h3>Expected response format (JSON):</h3>
 * <pre>{@code
 * {
 *   "results": [
 *     {"index": 0, "relevance_score": 0.95},
 *     {"index": 2, "relevance_score": 0.82},
 *     ...
 *   ]
 * }
 * }</pre>
 */
public class HttpRerankerClient implements RerankerClient {

    private static final Logger LOG = Logger.getLogger(HttpRerankerClient.class.getName());
    private static final Gson GSON = new Gson();

    private final RerankerSettings settings;
    private boolean available;

    public HttpRerankerClient(RerankerSettings settings) {
        this.settings = settings;
        this.available = settings.isEnabled()
                && settings.getApiUrl() != null
                && !settings.getApiUrl().trim().isEmpty();
    }

    @Override
    public float[] rerank(String query, List<String> passages) throws RerankerException {
        if (passages == null || passages.isEmpty()) {
            return new float[0];
        }
        if (query == null || query.trim().isEmpty()) {
            // No query → return neutral scores
            float[] zeros = new float[passages.size()];
            java.util.Arrays.fill(zeros, 0f);
            return zeros;
        }

        String url = settings.getApiUrl();

        // Ollama uses the same /api/embed endpoint as for embeddings — you just
        // load a different model (e.g. a cross-encoder / reranker model). There is
        // no separate "rerank" endpoint on Ollama, so we score by computing cosine
        // similarity between the query embedding and each passage embedding.
        if (url != null && url.contains("/api/embed")) {
            try {
                return rerankViaOllamaEmbed(query, passages);
            } catch (IOException e) {
                throw new RerankerException("Ollama embed rerank request failed: " + e.getMessage(), e);
            }
        }

        // Default: Jina / Cohere / TEI / vLLM compatible rerank protocol.
        JsonObject requestBody = new JsonObject();
        if (settings.getModel() != null && !settings.getModel().trim().isEmpty()) {
            requestBody.addProperty("model", settings.getModel());
        }
        requestBody.addProperty("query", query);

        JsonArray docsArray = new JsonArray();
        for (String passage : passages) {
            docsArray.add(passage);
        }
        requestBody.add("documents", docsArray);
        requestBody.addProperty("top_n", passages.size()); // we re-order ourselves

        String jsonBody = GSON.toJson(requestBody);
        LOG.fine("[Reranker] POST " + url + " with " + passages.size() + " passages");

        try {
            String response = doPost(url, jsonBody);
            return parseResponse(response, passages.size());
        } catch (IOException e) {
            throw new RerankerException("Reranker HTTP request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reranking via Ollama's {@code /api/embed} endpoint. Sends {@code [query, doc1, doc2, …]}
     * as a single batched input and scores each passage by cosine similarity against
     * the query embedding. Same endpoint as for normal embeddings — only the
     * configured model differs (e.g. a reranker / cross-encoder model).
     *
     * <p>Request shape:
     * <pre>{@code {"model": "<rerank-model>", "input": ["query", "doc1", "doc2", ...]}}</pre>
     *
     * <p>Response shape:
     * <pre>{@code {"embeddings": [[...], [...], ...]}}</pre>
     */
    private float[] rerankViaOllamaEmbed(String query, List<String> passages) throws IOException, RerankerException {
        JsonObject body = new JsonObject();
        if (settings.getModel() != null && !settings.getModel().trim().isEmpty()) {
            body.addProperty("model", settings.getModel());
        }
        JsonArray inputs = new JsonArray();
        inputs.add(query);
        for (String p : passages) {
            inputs.add(p != null ? p : "");
        }
        body.add("input", inputs);

        String jsonBody = GSON.toJson(body);
        LOG.fine("[Reranker/Ollama] POST " + settings.getApiUrl()
                + " model=" + settings.getModel()
                + " inputs=" + (passages.size() + 1));

        String response = doPost(settings.getApiUrl(), jsonBody);

        JsonElement root = JsonParser.parseString(response);
        if (!root.isJsonObject()) {
            throw new RerankerException("Unexpected Ollama embed response (not an object): " + response);
        }
        JsonObject obj = root.getAsJsonObject();
        JsonArray embeddings;
        if (obj.has("embeddings") && obj.get("embeddings").isJsonArray()) {
            embeddings = obj.getAsJsonArray("embeddings");
        } else if (obj.has("data") && obj.get("data").isJsonArray()) {
            // OpenAI-compatible fallback ({"data":[{"embedding":[...]}]})
            JsonArray data = obj.getAsJsonArray("data");
            embeddings = new JsonArray();
            for (JsonElement el : data) {
                if (el.isJsonObject() && el.getAsJsonObject().has("embedding")) {
                    embeddings.add(el.getAsJsonObject().getAsJsonArray("embedding"));
                }
            }
        } else {
            throw new RerankerException("Ollama embed response has no 'embeddings' field: " + response);
        }

        if (embeddings.size() != passages.size() + 1) {
            throw new RerankerException("Ollama embed: expected " + (passages.size() + 1)
                    + " embeddings (query + passages) but got " + embeddings.size());
        }

        float[] queryVec = toFloatArray(embeddings.get(0).getAsJsonArray());
        float[] scores = new float[passages.size()];
        for (int i = 0; i < passages.size(); i++) {
            float[] passageVec = toFloatArray(embeddings.get(i + 1).getAsJsonArray());
            scores[i] = cosineSimilarity(queryVec, passageVec);
        }
        return scores;
    }

    private static float[] toFloatArray(JsonArray arr) {
        float[] out = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = arr.get(i).getAsFloat();
        }
        return out;
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0f;
        int n = Math.min(a.length, b.length);
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) return 0f;
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }

    /**
     * Parse the reranker response. Supports two common formats:
     * <ol>
     *   <li>{@code {"results": [{"index": 0, "relevance_score": 0.95}, ...]}}</li>
     *   <li>{@code [{"index": 0, "score": 0.95}, ...]}</li>
     * </ol>
     */
    private float[] parseResponse(String responseJson, int passageCount) throws RerankerException {
        float[] scores = new float[passageCount];

        try {
            JsonElement root = JsonParser.parseString(responseJson);
            JsonArray results;

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("results")) {
                    results = obj.getAsJsonArray("results");
                } else if (obj.has("data")) {
                    // Some APIs use "data" instead of "results"
                    results = obj.getAsJsonArray("data");
                } else {
                    throw new RerankerException("Unexpected response format: no 'results' or 'data' field");
                }
            } else if (root.isJsonArray()) {
                results = root.getAsJsonArray();
            } else {
                throw new RerankerException("Unexpected response format: " + responseJson);
            }

            for (JsonElement elem : results) {
                JsonObject entry = elem.getAsJsonObject();
                int index = entry.has("index") ? entry.get("index").getAsInt() : -1;

                float score = 0f;
                if (entry.has("relevance_score")) {
                    score = entry.get("relevance_score").getAsFloat();
                } else if (entry.has("score")) {
                    score = entry.get("score").getAsFloat();
                }

                if (index >= 0 && index < passageCount) {
                    scores[index] = score;
                }
            }

            return scores;
        } catch (RerankerException e) {
            throw e;
        } catch (Exception e) {
            throw new RerankerException("Failed to parse reranker response: " + e.getMessage(), e);
        }
    }

    private String doPost(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);

        HttpURLConnection conn;
        if (settings.isUseProxy()) {
            // Proxy settings are resolved globally via ProxySelector in the app
            conn = (HttpURLConnection) url.openConnection();
        } else {
            conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        }

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(settings.getTimeoutSeconds() * 1000);
        conn.setReadTimeout(settings.getTimeoutSeconds() * 1000);
        conn.setDoOutput(true);

        String apiKey = settings.getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String error = readStream(conn.getErrorStream());
            throw new IOException("Reranker HTTP " + responseCode + ": " + error);
        }

        return readStream(conn.getInputStream());
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getDescription() {
        return (settings.getModel() != null ? settings.getModel() : "unknown")
                + " @ " + settings.getApiUrl();
    }

    @Override
    public int getCandidatePoolSize() {
        return settings.getCandidatePoolSize();
    }

    @Override
    public int getTopN() {
        return settings.getTopN();
    }

    @Override
    public float getScoreThreshold() {
        return settings.getScoreThreshold();
    }
}
