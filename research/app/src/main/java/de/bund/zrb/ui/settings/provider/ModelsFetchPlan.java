package de.bund.zrb.ui.settings.provider;

import java.util.Collections;
import java.util.Map;

/**
 * Beschreibung eines Modell-Listen-Abrufs für einen {@link ModelSlot}.
 *
 * <p>Wird vom Builder-Lambda ({@link ModelSlot.Builder#modelsFetcher}) auf Basis des
 * aktuellen Feldzustands eines Provider-Cards erzeugt. Enthält die GET-URL und
 * optionale HTTP-Header. Ein {@code null}-{@link #url} kennzeichnet einen
 * Validierungsfehler — in dem Fall wird {@link #errorHint} im Status-Label angezeigt,
 * ohne dass eine Anfrage abgesetzt wird.</p>
 */
public final class ModelsFetchPlan {

    public final String url;
    public final Map<String, String> headers;
    public final String errorHint;

    public ModelsFetchPlan(String url, Map<String, String> headers) {
        this(url, headers, null);
    }

    public ModelsFetchPlan(String url, Map<String, String> headers, String errorHint) {
        this.url = url;
        this.headers = headers != null ? headers : Collections.<String, String>emptyMap();
        this.errorHint = errorHint;
    }

    /** Fehlende Voraussetzungen (z.&nbsp;B. leere Base-URL) — keine Anfrage absetzen. */
    public static ModelsFetchPlan error(String hint) {
        return new ModelsFetchPlan(null, null, hint);
    }
}

