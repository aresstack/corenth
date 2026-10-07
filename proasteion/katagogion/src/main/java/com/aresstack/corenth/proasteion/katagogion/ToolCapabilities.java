package com.aresstack.corenth.proasteion.katagogion;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The mediated capability implementations a host offers to tools.
 *
 * <p>The composition point builds this from existing use cases. Which of them a particular
 * plugin may use is decided separately by the {@link ToolAdmissionPolicy}.
 */
public final class ToolCapabilities {

    private final LexicalSearch lexicalSearch;
    private final MediatedReading mediatedReading;

    private ToolCapabilities(Builder builder) {
        this.lexicalSearch = builder.lexicalSearch;
        this.mediatedReading = builder.mediatedReading;
    }

    /** Return a bundle without any capability. */
    public static ToolCapabilities none() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Return the capabilities that have an implementation. */
    public Set<ToolCapability> available() {
        EnumSet<ToolCapability> available = EnumSet.noneOf(ToolCapability.class);
        if (lexicalSearch != null) {
            available.add(ToolCapability.LEXICAL_SEARCH);
        }
        if (mediatedReading != null) {
            available.add(ToolCapability.MEDIATED_READING);
        }
        return Collections.unmodifiableSet(available);
    }

    LexicalSearch lexicalSearch() {
        return lexicalSearch;
    }

    MediatedReading mediatedReading() {
        return mediatedReading;
    }

    /** Builder for {@link ToolCapabilities}. */
    public static final class Builder {
        private LexicalSearch lexicalSearch;
        private MediatedReading mediatedReading;

        private Builder() {
        }

        public Builder lexicalSearch(LexicalSearch lexicalSearch) {
            if (lexicalSearch == null) {
                throw new IllegalArgumentException("lexicalSearch must not be null");
            }
            this.lexicalSearch = lexicalSearch;
            return this;
        }

        public Builder mediatedReading(MediatedReading mediatedReading) {
            if (mediatedReading == null) {
                throw new IllegalArgumentException("mediatedReading must not be null");
            }
            this.mediatedReading = mediatedReading;
            return this;
        }

        public ToolCapabilities build() {
            return new ToolCapabilities(this);
        }
    }
}
