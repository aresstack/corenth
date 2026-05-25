package de.bund.zrb.ui.settings.provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Beschreibt ein einzelnes Pflichtfeld einer Provider-Konfiguration.
 *
 * <p>Pflichtfelder können entweder facet-unabhängig sein (sie werden immer
 * gerendert, egal welche {@link Facet}s der Aufrufer aktiviert: URL, API-Key,
 * Auth-Header, Vendor, Port, Threads, Binary-Pfad, Keep-Alive, …) oder über
 * {@link #requiredFacet} an eine bestimmte Facet gebunden sein (z.&nbsp;B. die
 * beiden Responses-URLs der Public Cloud, die nur bei aktiver
 * {@link Facet#RESPONSES} sichtbar sind).</p>
 *
 * <p>Felder, bei denen <b>genau ein Modell</b> plus optional eine eigene URL /
 * Endpoint pro Funktion gewählt wird (Chat-/Embeddings-/Reranker-/Audio-Modell),
 * werden stattdessen über {@link ModelSlot}s beschrieben.</p>
 */
public final class FieldSpec implements ProviderDef.Item {

    public enum Type {
        /** Einfaches Textfeld ({@link javax.swing.JTextField}). */
        TEXT,
        /** Passwortfeld ({@link javax.swing.JPasswordField}) mit Toggle-Button. */
        PASSWORD,
        /** Editierbare Combobox ({@link javax.swing.JComboBox} mit {@code setEditable(true)}). */
        COMBO_EDITABLE,
        /** Feste Auswahl aus {@link FieldSpec#choices}. */
        COMBO_FIXED,
        /** Ganzzahl-Spinner ({@link javax.swing.JSpinner} mit {@link javax.swing.SpinnerNumberModel}). */
        INT_SPINNER,
        /** Checkbox ({@link javax.swing.JCheckBox}); {@link FieldSpec#label} steht am Checkbox-Text. */
        CHECKBOX,
        /**
         * Dynamische HTTP-Header-Tabelle (Spalten {@code Header} / {@code Wert}).
         * Schlüssel sind {@code <prefix>.<name>} mit {@code prefix = key},
         * z.&nbsp;B. {@code cloud.header.}.
         */
        HEADER_TABLE,
        /**
         * Reines Info-/Hinweis-Label (italic, grau). Kein Persistenz-Key; {@link FieldSpec#label}
         * enthält den HTML-Text. Eignet sich für „Feature wird vom Provider nicht unterstützt"-
         * Hinweise (typischerweise mit {@link FieldSpec#requiredFacet}).
         */
        INFO
    }

    public final Type type;
    public final String key;
    public final String label;
    public final String defaultValue;
    public final String tooltip;
    public final String[] choices;
    public final int spinnerMin, spinnerMax, spinnerStep;
    public final boolean withResetButton;
    /** Sektion-Header, der unmittelbar vor diesem Feld gerendert wird (oder {@code null}). */
    public final String section;
    /** Wenn gesetzt: dieses Feld wird nur gerendert, falls die Facet im aktiven Set enthalten ist. */
    public final Facet requiredFacet;
    /** Default-Inhalte für {@link Type#HEADER_TABLE} (Header → Wert), preserved insertion order. */
    public final Map<String, String> headerDefaults;

    private FieldSpec(Builder b) {
        this.type = b.type;
        this.key = b.key;
        this.label = b.label;
        this.defaultValue = b.defaultValue;
        this.tooltip = b.tooltip;
        this.choices = b.choices;
        this.spinnerMin = b.spinnerMin;
        this.spinnerMax = b.spinnerMax;
        this.spinnerStep = b.spinnerStep;
        this.withResetButton = b.withResetButton;
        this.section = b.section;
        this.requiredFacet = b.requiredFacet;
        this.headerDefaults = b.headerDefaults;
    }

    public static Builder text(String key, String label) {
        return new Builder(Type.TEXT, key, label);
    }

    public static Builder password(String key, String label) {
        return new Builder(Type.PASSWORD, key, label);
    }

    public static Builder comboEditable(String key, String label) {
        return new Builder(Type.COMBO_EDITABLE, key, label);
    }

    public static Builder comboFixed(String key, String label, String[] choices) {
        Builder b = new Builder(Type.COMBO_FIXED, key, label);
        b.choices = choices;
        return b;
    }

    public static Builder intSpinner(String key, String label, int min, int max, int step) {
        Builder b = new Builder(Type.INT_SPINNER, key, label);
        b.spinnerMin = min;
        b.spinnerMax = max;
        b.spinnerStep = step;
        return b;
    }

    public static Builder checkbox(String key, String label) {
        return new Builder(Type.CHECKBOX, key, label);
    }

    /** Header-Tabelle. Schlüssel-Präfix: {@code key} (z.&nbsp;B. {@code "cloud.header."}). */
    public static Builder headerTable(String keyPrefix, String label) {
        return new Builder(Type.HEADER_TABLE, keyPrefix, label);
    }

    /**
     * Reines Info-Label (italic, grau). {@code html} darf HTML enthalten und wird direkt
     * gerendert. Kein Persistenz-Key (intern wird ein eindeutiger Pseudo-Key vergeben).
     */
    public static Builder info(String html) {
        // Pseudo-Key, der nie persistiert wird; eindeutig genug durch System.identityHashCode beim Build.
        Builder b = new Builder(Type.INFO, "__info__", html);
        return b;
    }

    public static final class Builder {
        Type type;
        String key;
        String label;
        String defaultValue = "";
        String tooltip;
        String[] choices;
        int spinnerMin, spinnerMax, spinnerStep = 1;
        boolean withResetButton;
        String section;
        Facet requiredFacet;
        Map<String, String> headerDefaults = new LinkedHashMap<String, String>();

        private Builder(Type type, String key, String label) {
            this.type = type;
            this.key = key;
            this.label = label;
        }

        public Builder defaultValue(String v) { this.defaultValue = v; return this; }
        public Builder tooltip(String t) { this.tooltip = t; return this; }
        public Builder withResetButton() { this.withResetButton = true; return this; }
        /** Setzt einen Sektion-Header, der vor diesem Feld gerendert wird. */
        public Builder section(String s) { this.section = s; return this; }
        /** Macht das Feld facet-abhängig (nur sichtbar bei aktiver Facet). */
        public Builder requiredFacet(Facet f) { this.requiredFacet = f; return this; }
        /** Fügt einen Default-Header für {@link Type#HEADER_TABLE} hinzu (Reset-Inhalt). */
        public Builder headerDefault(String name, String value) {
            this.headerDefaults.put(name, value);
            return this;
        }

        public FieldSpec build() { return new FieldSpec(this); }
    }
}
