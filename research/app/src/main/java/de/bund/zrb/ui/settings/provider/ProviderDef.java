package de.bund.zrb.ui.settings.provider;

import de.bund.zrb.model.AiProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Datengetriebene Beschreibung einer Provider-Konfiguration.
 *
 * <p>Eine {@code ProviderDef} besteht aus einer geordneten Liste von {@link Item}s.
 * Jedes Item ist entweder ein {@link FieldSpec} oder ein {@link ModelSlot}. Die
 * Reihenfolge der {@code field(...)}- und {@code slot(...)}-Aufrufe bestimmt die
 * Render-Reihenfolge.</p>
 */
public final class ProviderDef {

    /** Marker-Interface für gerenderte Items ({@link FieldSpec} oder {@link ModelSlot}). */
    public interface Item {
    }

    public final AiProvider provider;
    public final List<Item> items;
    public final String subModeKey;
    public final String subModeLabel;
    public final List<SubMode> subModes;

    private ProviderDef(Builder b) {
        this.provider = b.provider;
        this.items = Collections.unmodifiableList(new ArrayList<Item>(b.items));
        this.subModeKey = b.subModeKey;
        this.subModeLabel = b.subModeLabel;
        this.subModes = Collections.unmodifiableList(new ArrayList<SubMode>(b.subModes));
    }

    public static Builder of(AiProvider provider) {
        Builder b = new Builder();
        b.provider = provider;
        return b;
    }

    /** Submodus mit eigenem Item-Set (z. B. PRIVATE_CLOUD: Compatible / Custom). */
    public static final class SubMode {
        public final String displayLabel;
        public final String storedValue;
        public final List<Item> items;

        private SubMode(SubModeBuilder b) {
            this.displayLabel = b.displayLabel;
            this.storedValue = b.storedValue;
            this.items = Collections.unmodifiableList(new ArrayList<Item>(b.items));
        }

        public static SubModeBuilder of(String displayLabel, String storedValue) {
            SubModeBuilder b = new SubModeBuilder();
            b.displayLabel = displayLabel;
            b.storedValue = storedValue;
            return b;
        }
    }

    public static final class SubModeBuilder {
        String displayLabel;
        String storedValue;
        final List<Item> items = new ArrayList<Item>();

        public SubModeBuilder field(FieldSpec f) {
            items.add(f);
            return this;
        }

        public SubModeBuilder slot(ModelSlot s) {
            items.add(s);
            return this;
        }

        public SubMode build() {
            return new SubMode(this);
        }
    }

    public static final class Builder {
        AiProvider provider;
        final List<Item> items = new ArrayList<Item>();
        String subModeKey;
        String subModeLabel;
        final List<SubMode> subModes = new ArrayList<SubMode>();

        public Builder field(FieldSpec f) {
            items.add(f);
            return this;
        }

        public Builder slot(ModelSlot s) {
            items.add(s);
            return this;
        }

        public Builder subModes(String key, String label, SubMode... modes) {
            this.subModeKey = key;
            this.subModeLabel = label;
            if (modes != null) {
                Collections.addAll(this.subModes, modes);
            }
            return this;
        }

        public ProviderDef build() {
            return new ProviderDef(this);
        }
    }
}

