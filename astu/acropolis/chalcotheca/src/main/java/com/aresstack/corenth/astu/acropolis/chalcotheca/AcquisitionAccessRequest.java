package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;

/**
 * Immutable request to prepare access for one external acquisition.
 *
 * <p>It names the target, the mediated operation that caused the acquisition, the actor and the
 * purpose, so that an authenticating adapter can scope its grant. It carries no credential
 * reference: which credential applies is a composition decision of the outer adapter.
 */
public final class AcquisitionAccessRequest {

    private final BookmarkUri target;
    private final ResourceOperation operation;
    private final ActorIdentity actor;
    private final String purpose;

    public AcquisitionAccessRequest(BookmarkUri target, ResourceOperation operation,
                                    ActorIdentity actor, String purpose) {
        if (target == null) throw new IllegalArgumentException("target must not be null");
        if (operation == null) throw new IllegalArgumentException("operation must not be null");
        if (actor == null) throw new IllegalArgumentException("actor must not be null");
        this.target = target;
        this.operation = operation;
        this.actor = actor;
        this.purpose = purpose;
    }

    /** Returns the resource to acquire. */
    public BookmarkUri target() { return target; }

    /** Returns the mediated operation that requires the acquisition. */
    public ResourceOperation operation() { return operation; }

    /** Returns the actor on whose behalf the counter acquires. */
    public ActorIdentity actor() { return actor; }

    /** Returns the stated purpose; may be {@code null}. */
    public String purpose() { return purpose; }

    @Override
    public String toString() {
        return "AcquisitionAccessRequest{" + operation + ", " + target + ", " + actor + "}";
    }
}
