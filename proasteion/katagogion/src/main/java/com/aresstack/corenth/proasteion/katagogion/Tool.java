package com.aresstack.corenth.proasteion.katagogion;

/**
 * A tool contributed by a plugin.
 *
 * <p>A tool receives only its invocation and a {@link ToolContext} holding the mediated
 * capabilities granted to its plugin. It never receives the host, the registry, connectors,
 * acquisition ports or secrets. Expected problems are reported as a failed {@link ToolResult};
 * the host converts unexpected runtime exceptions into {@link ToolFailureReason#EXECUTION_FAILED}.
 */
public interface Tool {

    /** Return the immutable declaration of this tool. */
    ToolDescriptor descriptor();

    /**
     * Run the tool.
     *
     * @param invocation validated invocation whose arguments match the descriptor
     * @param context    the mediated capabilities granted to the owning plugin
     * @return the typed result; never {@code null}
     */
    ToolResult execute(ToolInvocation invocation, ToolContext context);
}
