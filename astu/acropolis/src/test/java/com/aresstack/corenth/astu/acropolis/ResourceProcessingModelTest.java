package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Immutable run/plan/step/outcome/summary model of #10 Slice 4. */
public class ResourceProcessingModelTest {

    private static final VirtualResourceRef REF = new VirtualResourceRef(
            BookmarkUri.parse("file:///virtual/a.txt"), VirtualResourceKind.FILE);

    @Test
    public void standardPlan_hasTheLifecycleOrder() {
        assertEquals(Arrays.asList(
                ResourceProcessingStepType.INDEXING_POLICY_BEFORE_ACQUISITION,
                ResourceProcessingStepType.SOURCE_METADATA,
                ResourceProcessingStepType.MEDIATED_ACQUISITION,
                ResourceProcessingStepType.INDEXING_POLICY_WITH_SIZE,
                ResourceProcessingStepType.CHANGE_DETECTION,
                ResourceProcessingStepType.CONTENT_INSPECTION,
                ResourceProcessingStepType.LEXICAL_INDEXING,
                ResourceProcessingStepType.RECORD_UPDATE,
                ResourceProcessingStepType.DERIVED_STATE_CLEANUP),
                ResourceProcessingPlan.standard().steps());
    }

    @Test
    public void plan_acceptsOrderedSubsequences_only() {
        ResourceProcessingPlan plan = ResourceProcessingPlan.standard();
        assertTrue(plan.accepts(Collections.<ResourceProcessingStep>emptyList()));
        assertTrue(plan.accepts(Arrays.asList(
                completed(ResourceProcessingStepType.INDEXING_POLICY_BEFORE_ACQUISITION),
                completed(ResourceProcessingStepType.DERIVED_STATE_CLEANUP))));
        assertFalse(plan.accepts(Arrays.asList(
                completed(ResourceProcessingStepType.LEXICAL_INDEXING),
                completed(ResourceProcessingStepType.CONTENT_INSPECTION))));
        assertFalse(plan.accepts(Arrays.asList(
                completed(ResourceProcessingStepType.LEXICAL_INDEXING),
                completed(ResourceProcessingStepType.LEXICAL_INDEXING))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void plan_rejectsDuplicateSteps() {
        new ResourceProcessingPlan(Arrays.asList(
                ResourceProcessingStepType.MEDIATED_ACQUISITION, ResourceProcessingStepType.MEDIATED_ACQUISITION));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void plan_isImmutable() {
        ResourceProcessingPlan.standard().steps().clear();
    }

    @Test
    public void result_copiesItsSteps() {
        List<ResourceProcessingStep> steps = new ArrayList<ResourceProcessingStep>();
        steps.add(completed(ResourceProcessingStepType.MEDIATED_ACQUISITION));
        ProcessingResult result = ProcessingResult.of(REF, ResourceProcessingOutcome.INDEXED, "ok", null, steps);
        steps.clear();

        assertEquals(1, result.steps().size());
        try {
            result.steps().clear();
            fail("steps must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // immutable record
        }
    }

    @Test
    public void failureReason_isRequiredExactlyForFailedCancelledAndEmptyOutcomes() {
        ResourceProcessingFailure failure = new ResourceProcessingFailure(
                ResourceProcessingFailure.Reason.ACQUISITION_FAILED, "x");
        for (ResourceProcessingOutcome outcome : ResourceProcessingOutcome.values()) {
            boolean needsFailure = outcome == ResourceProcessingOutcome.FAILED
                    || outcome == ResourceProcessingOutcome.CANCELLED
                    || outcome == ResourceProcessingOutcome.NO_EXTRACTABLE_CONTENT;
            assertEquals(outcome.name(), needsFailure, accepts(outcome, failure));
            assertEquals(outcome.name(), !needsFailure, accepts(outcome, null));
        }
    }

    @Test
    public void outcomes_mapToCoarseStatus() {
        assertEquals(ProcessingResult.Status.INDEXED, ResourceProcessingOutcome.INDEXED.status());
        assertEquals(ProcessingResult.Status.UNCHANGED, ResourceProcessingOutcome.UNCHANGED.status());
        assertEquals(ProcessingResult.Status.DENIED, ResourceProcessingOutcome.DENIED.status());
        assertEquals(ProcessingResult.Status.FAILED, ResourceProcessingOutcome.NO_EXTRACTABLE_CONTENT.status());
        assertEquals(ProcessingResult.Status.FAILED, ResourceProcessingOutcome.FAILED.status());
        assertEquals(ProcessingResult.Status.CANCELLED, ResourceProcessingOutcome.CANCELLED.status());
        assertEquals(ProcessingResult.Status.REMOVED, ResourceProcessingOutcome.REMOVED.status());
    }

    @Test
    public void summary_countsEveryOutcome() {
        ResourceProcessingSummary summary = ResourceProcessingSummary.of(Arrays.asList(
                result(ResourceProcessingOutcome.INDEXED),
                result(ResourceProcessingOutcome.INDEXED),
                result(ResourceProcessingOutcome.UNCHANGED),
                result(ResourceProcessingOutcome.DENIED),
                result(ResourceProcessingOutcome.FAILED)));

        assertEquals(5, summary.total());
        assertEquals(2, summary.count(ResourceProcessingOutcome.INDEXED));
        assertEquals(1, summary.count(ResourceProcessingOutcome.UNCHANGED));
        assertEquals(1, summary.count(ResourceProcessingOutcome.DENIED));
        assertEquals(1, summary.count(ResourceProcessingOutcome.FAILED));
        assertEquals(0, summary.count(ResourceProcessingOutcome.CANCELLED));
        assertFalse(summary.isClean());
    }

    @Test
    public void run_validatesStepOrderAgainstThePlan() {
        ProcessingResult disordered = ProcessingResult.of(REF, ResourceProcessingOutcome.INDEXED, "x", null,
                Arrays.asList(completed(ResourceProcessingStepType.RECORD_UPDATE),
                        completed(ResourceProcessingStepType.LEXICAL_INDEXING)));
        try {
            new ResourceProcessingRun(new ResourceProcessingRunId("r"), ResourceProcessingPlan.standard(), 1L, 2L,
                    Collections.singletonList(disordered));
            fail("disordered steps must be rejected");
        } catch (IllegalArgumentException expected) {
            // the run records only plan-conformant executions
        }
    }

    @Test
    public void run_isImmutable_andSummarisesItsResults() {
        List<ProcessingResult> results = new ArrayList<ProcessingResult>();
        results.add(result(ResourceProcessingOutcome.INDEXED));
        ResourceProcessingRun run = new ResourceProcessingRun(new ResourceProcessingRunId("run-1"),
                ResourceProcessingPlan.standard(), 10L, 20L, results);
        results.clear();

        assertEquals(1, run.results().size());
        assertEquals(1, run.summary().count(ResourceProcessingOutcome.INDEXED));
        assertTrue(run.summary().isClean());
        assertEquals("run-1", run.id().value());
    }

    @Test(expected = IllegalArgumentException.class)
    public void runId_mustNotBeBlank() {
        new ResourceProcessingRunId("  ");
    }

    private static boolean accepts(ResourceProcessingOutcome outcome, ResourceProcessingFailure failure) {
        try {
            ProcessingResult.of(REF, outcome, "m", failure, Collections.<ResourceProcessingStep>emptyList());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static ProcessingResult result(ResourceProcessingOutcome outcome) {
        ResourceProcessingFailure failure = outcome == ResourceProcessingOutcome.FAILED
                ? new ResourceProcessingFailure(ResourceProcessingFailure.Reason.ACQUISITION_FAILED, "x") : null;
        return ProcessingResult.of(REF, outcome, "m", failure, Collections.<ResourceProcessingStep>emptyList());
    }

    private static ResourceProcessingStep completed(ResourceProcessingStepType type) {
        return ResourceProcessingStep.completed(type, null);
    }
}
