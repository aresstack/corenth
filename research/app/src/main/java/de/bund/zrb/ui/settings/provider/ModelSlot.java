package de.bund.zrb.ui.settings.provider;

import java.util.Map;
import java.util.function.Function;

/**
 * Beschreibt eine <i>Modell-Position</i> innerhalb einer Provider-Konfiguration.
 *
 * <p>Eine Modell-Position bündelt zusammengehörige Felder für genau eine Funktion
 * (Chat, Embeddings, Reranker, Audio, Responses):</p>
 * <ul>
 *   <li>verpflichtend: <b>Modell-Feld</b> ({@link #modelKey}) — Combobox oder Textfeld
 *       (siehe {@link #modelType})</li>
 *   <li>optional: eine <b>eigene URL</b> ({@link #urlKey})</li>
 *   <li>optional: ein <b>Endpoint-Pfad</b> relativ zur Base URL ({@link #endpointKey})</li>
 * </ul>
 *
 * <p>Der Aufrufer filtert über {@link Facet}: für den Embeddings-Override wird nur
 * der Slot mit {@code facet == EMBEDDINGS} gerendert; im allgemeinen Tab werden alle
 * Slots gerendert. Die Rendering-Logik ist in beiden Fällen identisch.</p>
 */
public final class ModelSlot implements ProviderDef.Item {

    /** Welche Komponente das Modellfeld wird. */
    public enum ModelType {
        /** Editierbare {@link javax.swing.JComboBox} (typisch für Cloud/Ollama). */
        COMBO_EDITABLE,
        /** Schlichtes {@link javax.swing.JTextField} (typisch für Dateipfade, z.&nbsp;B. llama.cpp/ONNX). */
        TEXT
    }

    public final Facet facet;
    public final ModelType modelType;
    public final String modelLabel;
    public final String modelKey;
    public final String modelDefault;
    public final String modelTooltip;

    /**
     * Optionales Label für die Sektion über diesem Slot. {@code null} ⇒ Renderer
     * leitet den Sektion-Header aus {@link #facet} ab (Chat / Embeddings / …).
     */
    public final String sectionLabel;

    /** {@code null}, falls dieser Provider keine separate URL pro Modell-Slot kennt. */
    public final String urlKey;
    public final String urlLabel;
    public final String urlDefault;
    public final String urlTooltip;

    /** {@code null}, falls keine Endpoint-Pfad-Konfiguration gewünscht ist. */
    public final String endpointKey;
    public final String endpointLabel;
    public final String endpointDefault;

    /**
     * Wenn {@code true}, wird neben der Modell-Combobox ein {@code 🔄}-Button
     * angeboten, der via {@code /v1/models} verfügbare Modelle abruft (Hook im
     * Renderer, derzeit ohne Implementierung — Platzhalter für Migration).
     */
    public final boolean withModelFetchButton;

    /**
     * Optionaler Lambda-Hook, der aus dem aktuellen Feldzustand des Provider-Cards
     * (Schlüssel → Wert) einen ausführbaren {@link ModelsFetchPlan} erzeugt. Ist er
     * gesetzt, rendert {@link ProviderCardRenderer} neben dem Modell-Combo einen
     * funktionsfähigen {@code 🔄}-Button mit Status-Label darunter.
     */
    public final Function<Map<String, String>, ModelsFetchPlan> modelsFetcher;

    /**
     * Optionaler Lambda-Hook, der aus dem aktuellen Feldzustand einen ausführbaren
     * {@link ConnectionTestPlan} (GET/POST gegen den jeweiligen Facet-Endpunkt) erzeugt.
     * Ist er gesetzt, rendert {@link ProviderCardRenderer} neben dem Modell-Combo
     * zusätzlich einen 🧪-Test-Button mit Status-Label.
     */
    public final Function<Map<String, String>, ConnectionTestPlan> connectionTester;

    private ModelSlot(Builder b) {
        this.facet = b.facet;
        this.modelType = b.modelType;
        this.modelLabel = b.modelLabel;
        this.modelKey = b.modelKey;
        this.modelDefault = b.modelDefault;
        this.modelTooltip = b.modelTooltip;
        this.sectionLabel = b.sectionLabel;
        this.urlKey = b.urlKey;
        this.urlLabel = b.urlLabel;
        this.urlDefault = b.urlDefault;
        this.urlTooltip = b.urlTooltip;
        this.endpointKey = b.endpointKey;
        this.endpointLabel = b.endpointLabel;
        this.endpointDefault = b.endpointDefault;
        this.withModelFetchButton = b.withModelFetchButton;
        this.modelsFetcher = b.modelsFetcher;
        this.connectionTester = b.connectionTester;
    }

    public static Builder of(Facet facet, String modelLabel, String modelKey) {
        Builder b = new Builder();
        b.facet = facet;
        b.modelLabel = modelLabel;
        b.modelKey = modelKey;
        return b;
    }

    public static final class Builder {
        Facet facet;
        ModelType modelType = ModelType.COMBO_EDITABLE;
        String modelLabel;
        String modelKey;
        String modelDefault = "";
        String modelTooltip;
        String sectionLabel;
        String urlKey;
        String urlLabel;
        String urlDefault = "";
        String urlTooltip;
        String endpointKey;
        String endpointLabel;
        String endpointDefault = "";
        boolean withModelFetchButton;
        Function<Map<String, String>, ModelsFetchPlan> modelsFetcher;
        Function<Map<String, String>, ConnectionTestPlan> connectionTester;

        public Builder modelDefault(String v) { this.modelDefault = v; return this; }
        public Builder modelTooltip(String t) { this.modelTooltip = t; return this; }
        public Builder modelType(ModelType t) { this.modelType = t; return this; }
        /** Überschreibt den standardmässig aus {@link #facet} abgeleiteten Sektion-Header. */
        public Builder sectionLabel(String s) { this.sectionLabel = s; return this; }

        public Builder url(String key, String label, String defaultValue) {
            this.urlKey = key;
            this.urlLabel = label;
            this.urlDefault = defaultValue;
            return this;
        }
        public Builder urlTooltip(String t) { this.urlTooltip = t; return this; }

        public Builder endpoint(String key, String label, String defaultValue) {
            this.endpointKey = key;
            this.endpointLabel = label;
            this.endpointDefault = defaultValue;
            return this;
        }

        public Builder withModelFetchButton() { this.withModelFetchButton = true; return this; }

        /**
         * Aktiviert den 🔄-Button und liefert die GET-URL + Header zur Laufzeit aus dem
         * aktuellen Feldzustand des Provider-Cards.
         */
        public Builder modelsFetcher(Function<Map<String, String>, ModelsFetchPlan> f) {
            this.modelsFetcher = f;
            this.withModelFetchButton = true;
            return this;
        }

        /**
         * Aktiviert den 🧪-Test-Button und liefert die GET-/POST-Test-URL + Header
         * (+ optionaler JSON-Body) zur Laufzeit aus dem aktuellen Feldzustand.
         */
        public Builder connectionTester(Function<Map<String, String>, ConnectionTestPlan> f) {
            this.connectionTester = f;
            return this;
        }

        public ModelSlot build() { return new ModelSlot(this); }
    }
}
