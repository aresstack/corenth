package de.bund.zrb.net;

import de.bund.zrb.model.Settings;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Gemeinsame Hilfslogik für Basic-Auth (Proxy-Authorization) und Ende-zu-Ende-Verschlüsselung
 * über alle {@link de.zrb.bund.api.ChatManager}-Implementierungen.
 *
 * <p>Die Aktivierung erfolgt per KI-Tab über {@code useProxyAuth} / {@code useE2e}-Flags in
 * der jeweiligen Config-Map (z. B. {@code aiConfig} für Chat / Allgemein). Die Credentials
 * liegen global in {@link Settings#proxyAuthUsername} / {@link Settings#proxyAuthPassword}
 * / {@link Settings#proxyE2ePassword} (Proxy-Tab).</p>
 *
 * <p>Für Rückwärtskompatibilität mit Settings-Dateien aus der Zeit, in der die Credentials
 * unter {@code aiConfig.ollama.proxy.*} / {@code aiConfig.ollama.e2e.password} lebten, wird
 * auf diese Keys zurückgegriffen, falls die globalen Felder leer sind. Wenn die Flags
 * {@code useProxyAuth}/{@code useE2e} überhaupt nicht in der Config stehen (alte Settings),
 * fällt das Verhalten auf „aktiv, sobald Credentials vorhanden" zurück — wie früher.</p>
 */
public final class ChatProxyAuthHelper {

    /** Header, mit dem die Server-Seite die E2E-Verschlüsselung erkennt. */
    public static final String HEADER_E2E_ENCRYPTED = "X-E2E-Encrypted";
    /** Header, mit dem die Server-Seite den ursprünglichen Content-Type erfährt. */
    public static final String HEADER_ORIGINAL_CONTENT_TYPE = "X-Original-Content-Type";

    private static final MediaType BIN = MediaType.get("application/octet-stream");

    private ChatProxyAuthHelper() { /* utility */ }

    /** Resultat: aufbereitete Daten für Request-Bau und Response-Behandlung. */
    public static final class Plan {
        public final boolean useProxyAuth;
        public final String proxyUsername;
        public final String proxyPassword;
        public final boolean useE2e;
        public final String e2ePassword;

        Plan(boolean useProxyAuth, String proxyUsername, String proxyPassword,
             boolean useE2e, String e2ePassword) {
            this.useProxyAuth = useProxyAuth;
            this.proxyUsername = proxyUsername;
            this.proxyPassword = proxyPassword;
            this.useE2e = useE2e;
            this.e2ePassword = e2ePassword;
        }
    }

    /**
     * Liest Flags + Credentials für einen ChatManager.
     *
     * @param settings  geladene Settings (für globale Credentials)
     * @param tabConfig Config-Map des aufrufenden Tabs (z. B. {@code settings.aiConfig})
     */
    public static Plan resolve(Settings settings, Map<String, String> tabConfig) {
        if (tabConfig == null) tabConfig = java.util.Collections.emptyMap();

        // Credentials (global), mit Legacy-Fallback aus aiConfig.
        String user = settings.proxyAuthUsername == null ? "" : settings.proxyAuthUsername;
        String pass = settings.proxyAuthPassword == null ? "" : settings.proxyAuthPassword;
        if (user.isEmpty() && settings.aiConfig != null) {
            user = settings.aiConfig.getOrDefault("ollama.proxy.username", "");
        }
        if (pass.isEmpty() && settings.aiConfig != null) {
            pass = settings.aiConfig.getOrDefault("ollama.proxy.password", "");
        }
        String e2e = settings.proxyE2ePassword == null ? "" : settings.proxyE2ePassword;
        if (e2e.isEmpty() && settings.aiConfig != null) {
            e2e = settings.aiConfig.getOrDefault("ollama.e2e.password", "");
        }

        // Flags: bevorzugt aus der jeweiligen tabConfig, sonst aus aiConfig.
        boolean useProxyAuth;
        if (tabConfig.containsKey("useProxyAuth")) {
            useProxyAuth = Boolean.parseBoolean(tabConfig.get("useProxyAuth"));
        } else if (settings.aiConfig != null && settings.aiConfig.containsKey("useProxyAuth")) {
            useProxyAuth = Boolean.parseBoolean(settings.aiConfig.get("useProxyAuth"));
        } else {
            // Legacy-Modus: Feature ist aktiv, sobald Credentials hinterlegt sind.
            useProxyAuth = !user.isEmpty() && !pass.isEmpty();
        }

        boolean useE2e;
        if (tabConfig.containsKey("useE2e")) {
            useE2e = Boolean.parseBoolean(tabConfig.get("useE2e"));
        } else if (settings.aiConfig != null && settings.aiConfig.containsKey("useE2e")) {
            useE2e = Boolean.parseBoolean(settings.aiConfig.get("useE2e"));
        } else {
            useE2e = !e2e.isEmpty();
        }

        return new Plan(
                useProxyAuth && !user.isEmpty() && !pass.isEmpty(),
                user, pass,
                useE2e && !e2e.isEmpty(),
                e2e);
    }

    /**
     * Hängt — falls aktiviert — den {@code Proxy-Authorization}-Header (Basic) an den Builder.
     * Verwendet bewusst {@code Proxy-Authorization} statt {@code Authorization}, um nicht
     * mit Bearer-Tokens (API-Key) zu kollidieren.
     */
    public static void applyProxyAuth(Request.Builder builder, Plan plan) {
        if (!plan.useProxyAuth) return;
        String credentials = plan.proxyUsername + ":" + plan.proxyPassword;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        builder.header("Proxy-Authorization", "Basic " + encoded);
    }

    /**
     * Baut den Request-Body: bei aktivem E2E wird {@code jsonPayload} verschlüsselt
     * und der Builder bekommt {@code X-E2E-Encrypted}- + {@code X-Original-Content-Type}-Header.
     *
     * @return RequestBody zum Posten
     * @throws Exception wenn die Verschlüsselung fehlschlägt
     */
    public static RequestBody buildBody(Request.Builder builder, String jsonPayload, Plan plan) throws Exception {
        if (plan.useE2e) {
            byte[] encrypted = E2ECrypto.encrypt(
                    jsonPayload.getBytes(StandardCharsets.UTF_8), plan.e2ePassword);
            builder.header(HEADER_E2E_ENCRYPTED, "true");
            builder.header(HEADER_ORIGINAL_CONTENT_TYPE, "application/json");
            return RequestBody.create(encrypted, BIN);
        }
        return RequestBody.create(jsonPayload, MediaType.get("application/json"));
    }
}

