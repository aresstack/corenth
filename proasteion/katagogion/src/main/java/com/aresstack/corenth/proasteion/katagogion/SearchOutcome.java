package com.aresstack.corenth.proasteion.katagogion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable outcome of a {@link LexicalSearch}: hits in ranking order, or an unavailability message. */
public final class SearchOutcome {

    private final List<SearchHit> hits;
    private final String unavailableMessage;

    private SearchOutcome(List<SearchHit> hits, String unavailableMessage) {
        this.hits = hits;
        this.unavailableMessage = unavailableMessage;
    }

    /** Create an outcome with the given hits in ranking order. */
    public static SearchOutcome hits(List<SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return new SearchOutcome(Collections.<SearchHit>emptyList(), null);
        }
        if (hits.contains(null)) {
            throw new IllegalArgumentException("hits must not contain null");
        }
        return new SearchOutcome(Collections.unmodifiableList(new ArrayList<SearchHit>(hits)), null);
    }

    /** Create an outcome stating that the search could not be served. */
    public static SearchOutcome unavailable(String message) {
        return new SearchOutcome(Collections.<SearchHit>emptyList(), Names.nullToEmpty(message));
    }

    public boolean isAvailable() {
        return unavailableMessage == null;
    }

    public List<SearchHit> hits() {
        return hits;
    }

    /** Return the unavailability message, or {@code null} when the search was served. */
    public String unavailableMessage() {
        return unavailableMessage;
    }
}
