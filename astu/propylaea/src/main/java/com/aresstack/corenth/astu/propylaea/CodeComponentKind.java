package com.aresstack.corenth.astu.propylaea;

/**
 * Language-neutral classification of a code component or of the expected target of a relation.
 */
public enum CodeComponentKind {

    /** An executable unit: Natural program/subprogram, COBOL program, load module. */
    PROGRAM,

    /** A callable block that is not a stand-alone program: Natural subroutine, COBOL paragraph. */
    SUBROUTINE,

    /** A JCL job. */
    JOB,

    /** A JCL job step. */
    STEP,

    /** A JCL procedure, cataloged or in-stream. */
    PROCEDURE,

    /** Text included at compile or interpretation time: Natural copycode, COBOL copybook, JCL include group. */
    COPYBOOK,

    /** A shared data definition: Natural local, parameter or global data area. */
    DATA_AREA,

    /** Persistent data accessed at run time: Adabas file via DDM, data set. */
    DATA_STORE
}
