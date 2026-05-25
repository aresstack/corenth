package de.bund.zrb.ui.settings.provider;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.net.ProxyResolver;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Generischer asynchroner Connection-Test für Provider-Endpunkte.
 *
 * <p>Führt einen einzelnen HTTP-Request (GET oder POST) gegen einen aus dem
 * {@link ConnectionTestPlan} gelieferten Endpunkt aus. Erfolg = HTTP 2xx.
 * 4xx mit klassischem Auth-Code (401/403) wird als <i>"erreichbar, aber Auth"
 * </i> orange markiert; 4xx/5xx sonst rot. Aufrufer setzen {@link JButton}
 * automatisch zurück (enable=false → true), damit Doppelklicks verhindert werden.</p>
 */
public final class ConnectionTester {

    private ConnectionTester() {}

    public static void testAsync(final JLabel statusLabel,
                                 final JButton triggerButton,
                                 final ConnectionTestPlan plan) {
        testAsync(statusLabel, triggerButton, plan, true);
    }

    /**
     * Wie {@link #testAsync(JLabel, JButton, ConnectionTestPlan)}, akzeptiert aber
     * ein explizites {@code useProxy}-Flag. Bei {@code false} wird der HTTP-Connect
     * direkt aufgebaut (kein {@link ProxyResolver}-Aufruf, kein globaler Proxy).
     */
    public static void testAsync(final JLabel statusLabel,
                                 final JButton triggerButton,
                                 final ConnectionTestPlan plan,
                                 final boolean useProxy) {
        if (plan == null) {
            setStatus(statusLabel, "⚠️ Kein Test-Plan", new Color(180, 100, 0));
            return;
        }
        if (plan.errorHint != null) {
            setStatus(statusLabel, plan.errorHint, new Color(180, 100, 0));
            return;
        }
        if (plan.url == null || plan.url.isEmpty()) {
            setStatus(statusLabel, "⚠️ Endpunkt nicht konfiguriert", new Color(180, 100, 0));
            return;
        }

        setStatus(statusLabel, "⏳ Teste Verbindung...", Color.BLACK);
        if (triggerButton != null) triggerButton.setEnabled(false);

        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(plan.url);
                    Proxy proxy = resolveProxy(plan.url, useProxy);
                    conn = (HttpURLConnection) (proxy != null
                            ? u.openConnection(proxy)
                            : u.openConnection());
                    conn.setRequestMethod(plan.method != null ? plan.method : "GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(15000);
                    if (plan.headers != null) {
                        for (Map.Entry<String, String> h : plan.headers.entrySet()) {
                            if (h.getKey() == null || h.getKey().isEmpty()) continue;
                            if (h.getValue() == null) continue;
                            conn.setRequestProperty(h.getKey(), h.getValue());
                        }
                    }
                    if ("POST".equalsIgnoreCase(plan.method) && plan.body != null) {
                        conn.setDoOutput(true);
                        if (plan.headers == null
                                || !plan.headers.containsKey("Content-Type")) {
                            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        }
                        OutputStream os = conn.getOutputStream();
                        try {
                            os.write(plan.body.getBytes(StandardCharsets.UTF_8));
                            os.flush();
                        } finally {
                            os.close();
                        }
                    }

                    final int code = conn.getResponseCode();
                    final String msg = readShortResponse(conn, code);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            if (code >= 200 && code < 300) {
                                setStatus(statusLabel,
                                        "✅ Verbindung erfolgreich (HTTP " + code + ")",
                                        new Color(0, 128, 0));
                            } else if (code == 401 || code == 403) {
                                setStatus(statusLabel,
                                        "⚠️ Erreichbar, aber Auth fehlt/ungültig (HTTP " + code + ")",
                                        new Color(180, 100, 0));
                            } else if (code == 404) {
                                setStatus(statusLabel,
                                        "❌ Endpunkt nicht gefunden (HTTP 404) — Pfad/Modell prüfen",
                                        Color.RED);
                            } else {
                                String tail = (msg != null && !msg.isEmpty())
                                        ? " — " + truncate(msg, 120) : "";
                                setStatus(statusLabel,
                                        "❌ HTTP " + code + tail, Color.RED);
                            }
                        }
                    });
                } catch (final Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            setStatus(statusLabel,
                                    "❌ " + ex.getClass().getSimpleName() + ": "
                                            + (ex.getMessage() != null ? ex.getMessage() : ""),
                                    Color.RED);
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                    if (triggerButton != null) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() { triggerButton.setEnabled(true); }
                        });
                    }
                }
            }
        }).start();
    }

    private static String readShortResponse(HttpURLConnection conn, int code) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    code >= 400 && conn.getErrorStream() != null
                            ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8));
            try {
                StringBuilder sb = new StringBuilder();
                String line;
                int n = 0;
                while ((line = r.readLine()) != null && n++ < 5) sb.append(line);
                return sb.toString();
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static void setStatus(JLabel l, String text, Color color) {
        if (l == null) return;
        l.setText(text);
        l.setForeground(color);
    }

    /**
     * Löst den Proxy für eine Ziel-URL via {@link ProxyResolver} auf. {@code null}
     * → direkter Connect; Fehler bei der Auflösung werden tolerant als
     * „kein Proxy" interpretiert (lieber direkt verbinden als gar nicht).
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
}

