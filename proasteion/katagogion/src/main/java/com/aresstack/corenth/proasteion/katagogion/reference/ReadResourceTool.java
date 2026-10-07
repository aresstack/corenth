package com.aresstack.corenth.proasteion.katagogion.reference;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.katagogion.ReadOutcome;
import com.aresstack.corenth.proasteion.katagogion.Tool;
import com.aresstack.corenth.proasteion.katagogion.ToolCapability;
import com.aresstack.corenth.proasteion.katagogion.ToolContext;
import com.aresstack.corenth.proasteion.katagogion.ToolDescriptor;
import com.aresstack.corenth.proasteion.katagogion.ToolFailureReason;
import com.aresstack.corenth.proasteion.katagogion.ToolInvocation;
import com.aresstack.corenth.proasteion.katagogion.ToolParameter;
import com.aresstack.corenth.proasteion.katagogion.ToolResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reference tool that reads a resource through the {@code MEDIATED_READING} capability.
 *
 * <p>The tool never opens the resource itself. A mediation denial becomes
 * {@link ToolFailureReason#ACCESS_DENIED} without any content.
 */
public final class ReadResourceTool implements Tool {

    /** Name under which the tool is installed. */
    public static final String NAME = "corenth.read";
    static final String URI = "uri";
    static final String KIND = "kind";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(NAME,
            "Read the text of a resource through Corenth's mediated access.",
            Arrays.asList(
                    ToolParameter.required(URI, "Resource URI, for example file:///path/to/file.txt."),
                    ToolParameter.optional(KIND, "Resource kind (default FILE).")),
            EnumSet.of(ToolCapability.MEDIATED_READING));

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        VirtualResourceRef ref;
        try {
            ref = new VirtualResourceRef(BookmarkUri.parse(invocation.argument(URI).trim()),
                    parseKind(invocation.argument(KIND)));
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(ToolFailureReason.INVALID_ARGUMENTS, e.getMessage());
        }
        ReadOutcome outcome = context.mediatedReading().read(ref);
        switch (outcome.status()) {
            case CONTENT:
                Map<String, String> record = new LinkedHashMap<String, String>();
                record.put("uri", ref.uri().toString());
                record.put("contentType", outcome.contentType());
                return ToolResult.success(outcome.text(), Collections.singletonList(record));
            case DENIED:
                return ToolResult.failure(ToolFailureReason.ACCESS_DENIED, outcome.message());
            default:
                return ToolResult.failure(ToolFailureReason.UNAVAILABLE, outcome.message());
        }
    }

    private static VirtualResourceKind parseKind(String value) {
        if (value == null || value.trim().isEmpty()) {
            return VirtualResourceKind.FILE;
        }
        try {
            return VirtualResourceKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(KIND + " must be one of " + Arrays.toString(VirtualResourceKind.values())
                    + ": " + value);
        }
    }
}
