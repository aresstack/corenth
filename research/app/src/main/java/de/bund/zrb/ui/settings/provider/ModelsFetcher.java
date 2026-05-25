package de.bund.zrb.ui.settings.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.net.ProxyResolver;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generischer asynchroner Modell-Listen-Abruf für die Provider-Karten.
 *
 * <p>Unterstützt sowohl das Ollama-Format ({@code GET /api/tags} → {@code models[].name})
 * als auch das OpenAI-Format ({@code GET /v1/models} → {@code data[].id}). Wird vom
 * {@link ProviderCardRenderer} aufgerufen, wenn an einem {@link ModelSlot} ein
 * {@link ModelSlot#modelsFetcher modelsFetcher}-Lambda konfiguriert ist.</p>
 */
public final class ModelsFetcher {

    private ModelsFetcher() {}

    /**
     * Lädt die Modell-Liste asynchron und befüllt die übergebene Combobox.
     * Der Status-Label wird vor/während/nach dem Abruf entsprechend aktualisiert.
     */
    public static void fetchAsync(final JComboBox<String> combo,
                                  final JLabel statusLabel,
                                  final String url,
                                  final Map<String, String> headers) {
        fetchAsync(combo, statusLabel, url, headers, true);
    }

    /**
     * Wie {@link #fetchAsync(JComboBox, JLabel, String, Map)}, akzeptiert aber explizit
     * ein {@code useProxy}-Flag. Wird typischerweise von einer per-Tab-Checkbox gespeist;
     * bei {@code false} wird {@link ProxyResolver} mit {@code useProxy=false} aufgerufen
     * und der HTTP-Connect erfolgt direkt (DIRECT), auch wenn ein globaler Proxy
     * konfiguriert ist.
     */
    public static void fetchAsync(final JComboBox<String> combo,
                                  final JLabel statusLabel,
                                  final String url,
                                  final Map<String, String> headers,
                                  final boolean useProxy) {
        setStatus(statusLabel, "⏳ Lade Modelle...", Color.BLACK);

        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(url);
                    Proxy proxy = resolveProxy(url, useProxy);
                    conn = (HttpURLConnection) (proxy != null
                            ? u.openConnection(proxy)
                            : u.openConnection());
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    if (headers != null) {
                        for (Map.Entry<String, String> h : headers.entrySet()) {
                            if (h.getKey() == null || h.getKey().isEmpty()) continue;
                            if (h.getValue() == null) continue;
                            conn.setRequestProperty(h.getKey(), h.getValue());
                        }
                    }
                    int code = conn.getResponseCode();
                    if (code != 200) {
                        final int c = code;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() { setStatus(statusLabel, "❌ HTTP " + c, Color.RED); }
                        });
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    try {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    } finally {
                        r.close();
                    }
                    final List<String> models = parseModelsResponse(sb.toString());
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            Object prevSel = combo.getEditor() != null
                                    ? combo.getEditor().getItem() : combo.getSelectedItem();
                            String prev = Objects.toString(prevSel, "");
                            combo.removeAllItems();
                            for (String m : models) combo.addItem(m);
                            combo.setSelectedItem(prev);
                            setStatus(statusLabel, "✅ " + models.size() + " Modelle geladen",
                                    new Color(0, 128, 0));
                        }
                    });
                } catch (final Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() { setStatus(statusLabel, "❌ " + ex.getMessage(), Color.RED); }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private static void setStatus(JLabel l, String text, Color color) {
        if (l == null) return;
        l.setText(text);
        l.setForeground(color);
    }

    /**
     * Löst den Proxy für eine Ziel-URL anhand der globalen Proxy-Einstellungen auf.
     * Bei {@code DISABLED}/lokalen Hosts wird {@code null} zurückgegeben (direkt).
     * Schlägt die Auflösung mit einer Exception fehl, wird ebenfalls direkt verbunden.
     */
    private static Proxy resolveProxy(String targetUrl) {
        return resolveProxy(targetUrl, true);
    }

    private static Proxy resolveProxy(String targetUrl, boolean useProxy) {
        try {
            ProxyResolver.ProxyResolution res = ProxyResolver.resolveForUrl(
                    targetUrl, SettingsHelper.load(), useProxy);
            if (res == null || res.isDirect()) return null;
            Proxy p = res.getProxy();
            return (p == null || p == Proxy.NO_PROXY) ? null : p;
        } catch (Exception e) {
            return null;
        }
    }

    /** Parst Ollama- ({@code models[].name}) und OpenAI-Antworten ({@code data[].id}). */
    private static List<String> parseModelsResponse(String json) {
        List<String> out = new ArrayList<String>();
        try {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            if (obj == null) return out;
            if (obj.has("models") && obj.get("models").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("models");
                for (JsonElement e : arr) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("name")) {
                        String n = e.getAsJsonObject().get("name").getAsString();
                        if (n.startsWith("models/")) n = n.substring("models/".length());
                        out.add(n);
                    }
                }
            } else if (obj.has("data") && obj.get("data").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("data");
                for (JsonElement e : arr) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
                        out.add(e.getAsJsonObject().get("id").getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
            // leere Liste — Status-Label bleibt auf "geladen" Erfolg, aber 0 Einträge.
        }
        return out;
    }
}

