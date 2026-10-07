package com.aresstack.corenth.proasteion.katagogion;

/**
 * A mediated capability a tool may use through its {@link ToolContext}.
 *
 * <p>Every capability is a thin view over an existing Corenth use case that already applies the
 * resource mediation of the inner city. There is deliberately no capability for file system,
 * network, acquisition or secret access.
 */
public enum ToolCapability {

    /** Lexical search over indexed resources (Acropolis search path). */
    LEXICAL_SEARCH,

    /** Reading resource content through the Chalcotheca archive counter under a host-bound actor. */
    MEDIATED_READING
}
