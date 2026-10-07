package com.aresstack.corenth.astu.acropolis;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Counts of the outcomes of one run (#10 Slice 4). Immutable.
 */
public final class ResourceProcessingSummary {

    private final Map<ResourceProcessingOutcome, Integer> counts;
    private final int total;

    private ResourceProcessingSummary(Map<ResourceProcessingOutcome, Integer> counts, int total) {
        this.counts = Collections.unmodifiableMap(counts);
        this.total = total;
    }

    /** Summarises the given results. */
    public static ResourceProcessingSummary of(List<ProcessingResult> results) {
        if (results == null) throw new IllegalArgumentException("results must not be null");
        Map<ResourceProcessingOutcome, Integer> counts =
                new EnumMap<ResourceProcessingOutcome, Integer>(ResourceProcessingOutcome.class);
        for (ResourceProcessingOutcome outcome : ResourceProcessingOutcome.values()) {
            counts.put(outcome, 0);
        }
        for (ProcessingResult result : results) {
            counts.put(result.outcome(), counts.get(result.outcome()) + 1);
        }
        return new ResourceProcessingSummary(counts, results.size());
    }

    /** Returns how many resources ended with the given outcome. */
    public int count(ResourceProcessingOutcome outcome) {
        return counts.get(outcome);
    }

    /** Returns the number of processed resources. */
    public int total() {
        return total;
    }

    /** Returns whether no resource failed or was cancelled. */
    public boolean isClean() {
        return count(ResourceProcessingOutcome.FAILED) == 0 && count(ResourceProcessingOutcome.CANCELLED) == 0;
    }

    @Override
    public String toString() {
        return "ResourceProcessingSummary{total=" + total + ", " + counts + "}";
    }
}
