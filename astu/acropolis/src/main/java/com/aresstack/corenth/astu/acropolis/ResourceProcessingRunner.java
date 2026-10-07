package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the lifecycle over a list of resources and records the run (#10 Slice 4).
 *
 * <p>Resources are processed sequentially in request order. A failure of one resource does not
 * stop the run. There is no scheduling, no background execution and no persistence of runs.
 */
public final class ResourceProcessingRunner {

    private final ResourceLifecycleCoordinator lifecycle;
    private final Clock clock;

    public ResourceProcessingRunner(ResourceLifecycleCoordinator lifecycle, Clock clock) {
        if (lifecycle == null) throw new IllegalArgumentException("lifecycle must not be null");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /**
     * Processes the resources and returns the immutable run record.
     *
     * @param id the run identifier
     * @param refs the resources to process; must not contain {@code null}
     * @return the run record
     */
    public ResourceProcessingRun run(ResourceProcessingRunId id, List<VirtualResourceRef> refs) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (refs == null) throw new IllegalArgumentException("refs must not be null");
        for (VirtualResourceRef ref : refs) {
            if (ref == null) throw new IllegalArgumentException("refs must not contain null");
        }
        long startedAt = clock.millis();
        List<ProcessingResult> results = new ArrayList<ProcessingResult>(refs.size());
        for (VirtualResourceRef ref : refs) {
            results.add(lifecycle.process(ref));
        }
        long finishedAt = Math.max(startedAt, clock.millis());
        return new ResourceProcessingRun(id, ResourceProcessingPlan.standard(), startedAt, finishedAt, results);
    }
}
