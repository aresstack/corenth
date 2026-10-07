package com.aresstack.corenth.astu.propylaea;

/**
 * Language-neutral kind of a {@link CodeRelation}. New kinds are added, never re-purposed.
 */
public enum RelationKind {

    /** Transfers control to another program, subroutine, step program or procedure. */
    CALLS,

    /** Pulls in text or data definitions: copycode, copybook, data area, JCL include group. */
    INCLUDES,

    /** Reads persistent data without changing it. */
    READS,

    /** Creates, changes or deletes persistent data. */
    WRITES
}
