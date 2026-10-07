package com.aresstack.corenth.astu.acropolis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The immutable record of one run over a list of resources (#10 Slice 4).
 *
 * <p>A run records behaviour: which plan applied, when it ran, and the per-resource results in
 * request order. It owns no connector, index or record and is not persisted; resource facts stay
 * in the #33 records, so a failed run never changes a resource history.
 */
public final class ResourceProcessingRun {

    private final ResourceProcessingRunId id;
    private final ResourceProcessingPlan plan;
    private final long startedAtMillis;
    private final long finishedAtMillis;
    private final List<ProcessingResult> results;
    private final ResourceProcessingSummary summary;

    public ResourceProcessingRun(ResourceProcessingRunId id, ResourceProcessingPlan plan,
                                 long startedAtMillis, long finishedAtMillis, List<ProcessingResult> results) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (plan == null) throw new IllegalArgumentException("plan must not be null");
        if (results == null) throw new IllegalArgumentException("results must not be null");
        if (finishedAtMillis < startedAtMillis) {
            throw new IllegalArgumentException("run must not finish before it starts");
        }
        for (ProcessingResult result : results) {
            if (result == null) throw new IllegalArgumentException("result must not be null");
            if (!plan.accepts(result.steps())) {
                throw new IllegalArgumentException("steps do not follow the plan: " + result.steps());
            }
        }
        this.id = id;
        this.plan = plan;
        this.startedAtMillis = startedAtMillis;
        this.finishedAtMillis = finishedAtMillis;
        this.results = Collections.unmodifiableList(new ArrayList<ProcessingResult>(results));
        this.summary = ResourceProcessingSummary.of(this.results);
    }

    public ResourceProcessingRunId id() { return id; }

    public ResourceProcessingPlan plan() { return plan; }

    public long startedAtMillis() { return startedAtMillis; }

    public long finishedAtMillis() { return finishedAtMillis; }

    /** Returns the per-resource results in request order. */
    public List<ProcessingResult> results() { return results; }

    public ResourceProcessingSummary summary() { return summary; }

    @Override
    public String toString() {
        return "ResourceProcessingRun{" + id + ", " + summary + "}";
    }
}
