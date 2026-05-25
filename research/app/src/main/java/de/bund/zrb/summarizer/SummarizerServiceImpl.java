package de.bund.zrb.summarizer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.model.AiProvider;
import de.zrb.bund.api.SummarizeOptions;
import de.zrb.bund.api.SummarizerService;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default-Implementierung von {@link SummarizerService}.
 *
 * <p>Liest seine Konfiguration zum jedem Aufruf <em>frisch</em> aus
 * {@link SummarizerSettings#fromStoredConfig()}, sodass Änderungen in den
 * Einstellungen sofort wirksam werden (keine Reinitialisierung nötig).</p>
 *
 * <p>Cache ist eine simple LRU-Map (synchronisiert) — bei
 * {@code cacheEnabled=false} wird sie ignoriert.</p>
 */
public final class SummarizerServiceImpl implements SummarizerService {

    private static final Logger LOG = Logger.getLogger(SummarizerServiceImpl.class.getName());

    private static final SummarizerServiceImpl INSTANCE = new SummarizerServiceImpl();

    public static SummarizerService get() { return INSTANCE; }

    private final ExecutorService asyncPool = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "summarizer-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    /** LRU-Cache. Wird beim ersten Zugriff anhand der Settings dimensioniert. */
    private Map<String, String> cache;
    private int cacheCapacity;

    private SummarizerServiceImpl() {}

    // ── Public API ──────────────────────────────────────────────────

    @Override
    public boolean isDedicated() {
        return SummarizerSettings.fromStoredConfig().isEnabled();
    }

    @Override
    public boolean isUmlSummarizationEnabled() {
        return SummarizerSettings.fromStoredConfig().isUmlSummarizationEnabled();
    }

    @Override
    public String summarize(String text, SummarizeOptions opts) {
        if (text == null || text.trim().isEmpty()) return safeFallback(opts);
        if (opts == null) opts = SummarizeOptions.label(60);

        SummarizerSettings cfg = SummarizerSettings.fromStoredConfig();

        // Wenn der Aufruf aus dem UML-Diagrammkontext kommt, kann der Caller
        // das per Options-Hinweis erkennbar machen. Der UML-Schalter wird hier
        // NICHT geprüft (Aufrufer in OutlineToMermaidConverter prüfen das schon).
        String key = cacheKey(cfg, opts, text);
        String cached = cacheGet(cfg, key);
        if (cached != null) return cached;

        String result;
        try {
            String prompt = buildUserPrompt(text, opts);
            result = callProvider(cfg, prompt);
            result = sanitize(result, opts);
            if (result.isEmpty()) result = safeFallback(opts);
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Summarizer call failed: " + ex.getMessage(), ex);
            result = safeFallback(opts);
        }

        cachePut(cfg, key, result);
        return result;
    }

    @Override
    public String quickTask(String systemPrompt, String userText, int maxTokens) {
        if (userText == null || userText.trim().isEmpty()) return "";
        SummarizerSettings cfg = SummarizerSettings.fromStoredConfig();
        try {
            // Override the system prompt for this single call.
            String body = buildBody(cfg, systemPrompt != null ? systemPrompt : cfg.getSystemPrompt(),
                    userText, Math.max(8, maxTokens));
            String raw = httpPostJson(cfg, body);
            return sanitize(extractContent(cfg, raw), null);
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Summarizer quickTask failed: " + ex.getMessage(), ex);
            return "";
        }
    }

    @Override
    public void summarizeAsync(final String text, final SummarizeOptions opts,
                               final Consumer<String> callback) {
        if (callback == null) return;
        asyncPool.submit(new Runnable() {
            @Override public void run() {
                final String r = summarize(text, opts);
                if (SwingUtilities.isEventDispatchThread()) {
                    callback.accept(r);
                } else {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() { callback.accept(r); }
                    });
                }
            }
        });
    }

    // ── Prompt Building ─────────────────────────────────────────────

    private static String buildUserPrompt(String text, SummarizeOptions opts) {
        StringBuilder sb = new StringBuilder();
        sb.append("STIL: ").append(opts.style().name()).append('\n');
        if (opts.style() == SummarizeOptions.Style.BULLETS) {
            sb.append("BULLETS_MAX: ").append(opts.maxBullets()).append('\n');
        }
        sb.append("MAX_ZEICHEN: ").append(opts.maxChars()).append('\n');
        if (opts.purposeHint() != null && !opts.purposeHint().isEmpty()) {
            sb.append("KONTEXT: ").append(opts.purposeHint()).append('\n');
        }
        sb.append("\nTEXT:\n");
        // Eingabe-Kappung — Summarizer darf nicht mit Riesen-Bodies geflutet werden.
        String safeText = text.length() > 4000 ? text.substring(0, 4000) + "\n…" : text;
        sb.append(safeText);
        return sb.toString();
    }

    private static String buildBody(SummarizerSettings cfg, String systemPrompt,
                                    String userPrompt, int maxTokens) {
        AiProvider provider = cfg.getProvider();
        Gson gson = new Gson();
        // Ollama-/OpenAI-Chat-Body-Format unterscheiden sich minimal.
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.getModel());

        JsonArray msgs = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt);
        msgs.add(sys);
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", userPrompt);
        msgs.add(usr);
        body.add("messages", msgs);

        body.addProperty("stream", false);

        if (provider == AiProvider.OLLAMA) {
            JsonObject options = new JsonObject();
            options.addProperty("num_predict", maxTokens);
            options.addProperty("temperature", 0.0);
            body.add("options", options);
        } else {
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("temperature", 0.0);
        }
        return gson.toJson(body);
    }

    // ── HTTP Call ───────────────────────────────────────────────────

    private String callProvider(SummarizerSettings cfg, String userPrompt) throws Exception {
        if (cfg.getApiUrl() == null || cfg.getApiUrl().isEmpty()) {
            throw new IllegalStateException("Summarizer-Provider nicht konfiguriert (apiUrl leer)");
        }
        if (cfg.getModel() == null || cfg.getModel().isEmpty()) {
            throw new IllegalStateException("Summarizer-Provider nicht konfiguriert (Modell leer)");
        }
        String body = buildBody(cfg, cfg.getSystemPrompt(), userPrompt, cfg.getMaxTokens());
        String raw = httpPostJson(cfg, body);
        return extractContent(cfg, raw);
    }

    private static String httpPostJson(SummarizerSettings cfg, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(cfg.getApiUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(Math.max(2000, cfg.getTimeoutSeconds() * 1000));
            conn.setReadTimeout(Math.max(2000, cfg.getTimeoutSeconds() * 1000));
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + cfg.getApiKey());
            }
            OutputStream os = conn.getOutputStream();
            try { os.write(body.getBytes(StandardCharsets.UTF_8)); } finally { os.close(); }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = readAll(code >= 400 && conn.getErrorStream() != null
                        ? conn.getErrorStream() : conn.getInputStream());
                throw new IllegalStateException("HTTP " + code + " — " + abbreviate(err, 200));
            }
            return readAll(conn.getInputStream());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(java.io.InputStream in) throws java.io.IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            r.close();
        }
    }

    /** Extrahiert den Antworttext aus Ollama- bzw. OpenAI-Chat-Antworten. */
    private static String extractContent(SummarizerSettings cfg, String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            JsonObject obj = new Gson().fromJson(raw, JsonObject.class);
            if (obj == null) return "";
            // Ollama (/api/chat, non-stream): { "message": { "role": "assistant", "content": "…" } }
            if (cfg.getProvider() == AiProvider.OLLAMA
                    && obj.has("message")
                    && obj.get("message").isJsonObject()) {
                JsonElement c = obj.getAsJsonObject("message").get("content");
                return c != null ? c.getAsString() : "";
            }
            // OpenAI-compatible: { "choices": [ { "message": { "content": "…" } } ] }
            if (obj.has("choices") && obj.get("choices").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("choices");
                if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                    JsonObject ch = arr.get(0).getAsJsonObject();
                    if (ch.has("message") && ch.get("message").isJsonObject()) {
                        JsonElement c = ch.getAsJsonObject("message").get("content");
                        return c != null ? c.getAsString() : "";
                    }
                    if (ch.has("text")) return ch.get("text").getAsString();
                }
            }
            // Fallback: Ollama generate-style { "response": "..." }
            if (obj.has("response")) return obj.get("response").getAsString();
        } catch (Exception ignored) {
            // Antwort war kein JSON oder unerwartetes Format → leerer String → Fallback
        }
        return "";
    }

    // ── Sanitisierung & Cache ───────────────────────────────────────

    private static String sanitize(String s, SummarizeOptions opts) {
        if (s == null) return "";
        String out = s.trim();
        // Häufige Modell-Artefakte entfernen.
        if (out.startsWith("```")) {
            int nl = out.indexOf('\n');
            if (nl > 0) out = out.substring(nl + 1);
            if (out.endsWith("```")) out = out.substring(0, out.length() - 3).trim();
        }
        // Anführungs-Wrapper entfernen.
        if (out.length() > 1
                && (out.startsWith("\"") && out.endsWith("\"")
                ||  out.startsWith("\u201E") /* „ */ && out.endsWith("\u201C") /* " */)) {
            out = out.substring(1, out.length() - 1);
        }
        // Newlines kollabieren für LABEL-Stil.
        if (opts != null && opts.style() == SummarizeOptions.Style.LABEL) {
            out = out.replace("\n", " ").replace("\r", " ");
            while (out.contains("  ")) out = out.replace("  ", " ");
        }
        // Harte Längen-Kappung.
        if (opts != null && out.length() > opts.maxChars()) {
            out = out.substring(0, opts.maxChars()).trim();
            // letztes vollständiges Wort
            int sp = out.lastIndexOf(' ');
            if (sp > opts.maxChars() / 2) out = out.substring(0, sp);
            out = out + "…";
        }
        return out.trim();
    }

    private static String safeFallback(SummarizeOptions opts) {
        return opts != null && opts.fallback() != null ? opts.fallback() : "";
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String cacheKey(SummarizerSettings cfg, SummarizeOptions opts, String text) {
        return cfg.getProvider().name() + "|" + cfg.getModel() + "|" + opts.style().name()
                + "|" + opts.maxChars() + "|" + Integer.toHexString(text.hashCode());
    }

    private synchronized String cacheGet(SummarizerSettings cfg, String key) {
        if (!cfg.isCacheEnabled()) return null;
        ensureCache(cfg);
        return cache != null ? cache.get(key) : null;
    }

    private synchronized void cachePut(SummarizerSettings cfg, String key, String value) {
        if (!cfg.isCacheEnabled()) return;
        ensureCache(cfg);
        if (cache == null) return;
        cache.put(key, value);
    }

    private void ensureCache(SummarizerSettings cfg) {
        int target = Math.max(16, cfg.getCacheSize());
        if (cache == null || target != cacheCapacity) {
            final int cap = target;
            // LRU via LinkedHashMap mit accessOrder=true.
            cache = Collections.synchronizedMap(new LinkedHashMap<String, String>(cap, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > cap;
                }
            });
            cacheCapacity = target;
        }
    }

    /** Test-Hook: leert den Cache. */
    public synchronized void clearCache() {
        if (cache != null) cache.clear();
    }
}

