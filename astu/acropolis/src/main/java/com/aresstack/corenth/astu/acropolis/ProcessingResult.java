package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of processing a single resource through the walking skeleton pipeline.
 */
public final class ProcessingResult {

    public enum Status {
        /** Successfully indexed. */
        INDEXED,
        /** Denied by policy. */
        DENIED,
        /** Skipped because content has not changed. */
        UNCHANGED,
        /** Failed during processing. */
        FAILED
    }

    private final VirtualResourceRef ref;
    private final Status status;
    private final String message;
    private final List<String> warnings;

    private ProcessingResult(VirtualResourceRef ref, Status status, String message, List<String> warnings) {
        this.ref = ref;
        this.status = status;
        this.message = message;
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public static ProcessingResult indexed(VirtualResourceRef ref) {
        return new ProcessingResult(ref, Status.INDEXED, "indexed successfully",
                Collections.<String>emptyList());
    }

    public static ProcessingResult denied(VirtualResourceRef ref, String reason) {
        return new ProcessingResult(ref, Status.DENIED, reason,
                Collections.<String>emptyList());
    }

    public static ProcessingResult unchanged(VirtualResourceRef ref) {
        return new ProcessingResult(ref, Status.UNCHANGED, "content unchanged",
                Collections.<String>emptyList());
    }

    public static ProcessingResult failed(VirtualResourceRef ref, String error) {
        return new ProcessingResult(ref, Status.FAILED, error,
                Collections.<String>emptyList());
    }

    /** Returns the resource reference. */
    public VirtualResourceRef ref() { return ref; }

    /** Returns the processing status. */
    public Status status() { return status; }

    /** Returns a human-readable message about the outcome. */
    public String message() { return message; }

    /** Returns any warnings. */
    public List<String> warnings() { return warnings; }

    @Override
    public String toString() {
        return "ProcessingResult{" + status + ", " + ref + ", " + message + "}";
    }
}
