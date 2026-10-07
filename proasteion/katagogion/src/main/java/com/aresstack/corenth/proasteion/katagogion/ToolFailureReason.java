package com.aresstack.corenth.proasteion.katagogion;

/** Typed reason why a tool invocation did not succeed. */
public enum ToolFailureReason {

    /** No installed tool carries the requested name. */
    UNKNOWN_TOOL,

    /** A required argument is missing, an undeclared argument was passed, or a value is malformed. */
    INVALID_ARGUMENTS,

    /** The tool tried to use a capability its plugin was not granted. */
    CAPABILITY_NOT_GRANTED,

    /** The mediated use case withheld the result (for example a Tamias denial). */
    ACCESS_DENIED,

    /** The mediated use case could not serve the request (for example an unreadable index). */
    UNAVAILABLE,

    /** The tool failed with an unexpected exception or returned no result. */
    EXECUTION_FAILED
}
