package de.bund.zrb.ui.settings.provider;

import java.util.Collections;
import java.util.Map;

/**
 * Plan zum Test einer Provider-Verbindung (Chat / Embeddings / Reranker / Audio).
 *
 * <p>Wird aus dem aktuellen Feldzustand eines Provider-Cards (Map&lt;Key,Wert&gt;)
 * über {@link ModelSlot#connectionTester} erzeugt und vom
 * {@link ConnectionTester} ausgeführt. Trennt damit die provider-spezifische
 * URL-/Payload-Konstruktion vollständig von der UI-Logik.</p>
 */
public final class ConnectionTestPlan {

    public final String method;       // "GET" oder "POST"
    public final String url;
    public final Map<String, String> headers;
    public final String body;         // null bei GET
    public final String errorHint;    // != null ⇒ Test wird nicht ausgeführt, Hint wird angezeigt

    public ConnectionTestPlan(String method, String url,
                              Map<String, String> headers, String body) {
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.body = body;
        this.errorHint = null;
    }

    private ConnectionTestPlan(String errorHint) {
        this.method = null;
        this.url = null;
        this.headers = Collections.emptyMap();
        this.body = null;
        this.errorHint = errorHint;
    }

    /** Voraussetzung für den Test fehlt (z. B. URL/API-Key noch nicht gesetzt). */
    public static ConnectionTestPlan error(String hint) {
        return new ConnectionTestPlan(hint);
    }

    /** Bequemer GET-Test (z. B. Models-Endpoint). */
    public static ConnectionTestPlan get(String url, Map<String, String> headers) {
        return new ConnectionTestPlan("GET", url, headers, null);
    }

    /** Bequemer POST-Test mit JSON-Body. */
    public static ConnectionTestPlan postJson(String url, Map<String, String> headers, String jsonBody) {
        return new ConnectionTestPlan("POST", url, headers, jsonBody);
    }
}

