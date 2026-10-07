package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The immutable record of processing a single resource through the lifecycle.
 *
 * <p>Since #10 Slice 4 it records the executed {@link #steps()}, the fine-grained
 * {@link #outcome()} and, for failures, cancellations and empty extractions, a typed
 * {@link #failure()}. {@link #status()} remains the coarse compatibility view.
 */
public final class ProcessingResult {

    /** Coarse status; see {@link ResourceProcessingOutcome#status()} for the mapping. */
    public enum Status {
        /** Successfully indexed. */
        INDEXED,
        /** Denied by policy. */
        DENIED,
        /** Skipped because content has not changed. */
        UNCHANGED,
        /** Failed during processing, including extraction without indexable text. */
        FAILED,
        /** A credential request was cancelled. */
        CANCELLED,
        /** The resource was confirmed removed at the source. */
        REMOVED
    }

    private final VirtualResourceRef ref;
    private final ResourceProcessingOutcome outcome;
    private final String message;
    private final ResourceProcessingFailure failure;
    private final List<ResourceProcessingStep> steps;
    private final List<String> warnings;

    private ProcessingResult(VirtualResourceRef ref, ResourceProcessingOutcome outcome, String message,
                             ResourceProcessingFailure failure, List<ResourceProcessingStep> steps,
                             List<String> warnings) {
        if (outcome == null) throw new IllegalArgumentException("outcome must not be null");
        boolean needsFailure = outcome == ResourceProcessingOutcome.FAILED
                || outcome == ResourceProcessingOutcome.CANCELLED
                || outcome == ResourceProcessingOutcome.NO_EXTRACTABLE_CONTENT;
        if (needsFailure && failure == null) {
            throw new IllegalArgumentException(outcome + " requires a failure reason");
        }
        if (!needsFailure && failure != null) {
            throw new IllegalArgumentException(outcome + " must not carry a failure reason");
        }
        this.ref = ref;
        this.outcome = outcome;
        this.message = message;
        this.failure = failure;
        this.steps = Collections.unmodifiableList(new ArrayList<ResourceProcessingStep>(steps));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    /** Creates a result with recorded steps. */
    public static ProcessingResult of(VirtualResourceRef ref, ResourceProcessingOutcome outcome, String message,
                                      ResourceProcessingFailure failure, List<ResourceProcessingStep> steps) {
        if (steps == null) throw new IllegalArgumentException("steps must not be null");
        return new ProcessingResult(ref, outcome, message, failure, steps, Collections.<String>emptyList());
    }

    public static ProcessingResult indexed(VirtualResourceRef ref) {
        return of(ref, ResourceProcessingOutcome.INDEXED, "indexed successfully", null,
                Collections.<ResourceProcessingStep>emptyList());
    }

    public static ProcessingResult denied(VirtualResourceRef ref, String reason) {
        return of(ref, ResourceProcessingOutcome.DENIED, reason, null,
                Collections.<ResourceProcessingStep>emptyList());
    }

    public static ProcessingResult unchanged(VirtualResourceRef ref) {
        return of(ref, ResourceProcessingOutcome.UNCHANGED, "content unchanged", null,
                Collections.<ResourceProcessingStep>emptyList());
    }

    public static ProcessingResult failed(VirtualResourceRef ref, String error) {
        return of(ref, ResourceProcessingOutcome.FAILED, error,
                new ResourceProcessingFailure(ResourceProcessingFailure.Reason.INVALID_REQUEST, error),
                Collections.<ResourceProcessingStep>emptyList());
    }

    /** Returns the resource reference. */
    public VirtualResourceRef ref() { return ref; }

    /** Returns the coarse processing status. */
    public Status status() { return outcome.status(); }

    /** Returns the fine-grained outcome. */
    public ResourceProcessingOutcome outcome() { return outcome; }

    /** Returns the typed failure for FAILED, CANCELLED and NO_EXTRACTABLE_CONTENT, otherwise {@code null}. */
    public ResourceProcessingFailure failure() { return failure; }

    /** Returns the executed steps in order. */
    public List<ResourceProcessingStep> steps() { return steps; }

    /** Returns a human-readable message about the outcome. */
    public String message() { return message; }

    /** Returns any warnings. */
    public List<String> warnings() { return warnings; }

    @Override
    public String toString() {
        return "ProcessingResult{" + outcome + ", " + ref + ", " + message + "}";
    }
}
