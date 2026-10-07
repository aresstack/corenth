package com.aresstack.corenth.proasteion.katagogion.reference;

import com.aresstack.corenth.proasteion.katagogion.SearchHit;
import com.aresstack.corenth.proasteion.katagogion.SearchOutcome;
import com.aresstack.corenth.proasteion.katagogion.Tool;
import com.aresstack.corenth.proasteion.katagogion.ToolCapability;
import com.aresstack.corenth.proasteion.katagogion.ToolContext;
import com.aresstack.corenth.proasteion.katagogion.ToolDescriptor;
import com.aresstack.corenth.proasteion.katagogion.ToolFailureReason;
import com.aresstack.corenth.proasteion.katagogion.ToolInvocation;
import com.aresstack.corenth.proasteion.katagogion.ToolParameter;
import com.aresstack.corenth.proasteion.katagogion.ToolResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference tool that searches indexed resources through the {@code LEXICAL_SEARCH} capability.
 *
 * <p>It reads no files and opens no index itself; everything goes through {@link ToolContext}.
 */
public final class LexicalSearchTool implements Tool {

    /** Name under which the tool is installed. */
    public static final String NAME = "corenth.search";
    static final String QUERY = "query";
    static final String MAX_HITS = "max_hits";
    static final int DEFAULT_MAX_HITS = 10;
    static final int LIMIT_MAX_HITS = 50;

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(NAME,
            "Search resources that Corenth has already indexed.",
            Arrays.asList(
                    ToolParameter.required(QUERY, "Search text."),
                    ToolParameter.optional(MAX_HITS, "Maximum number of hits, 1 to " + LIMIT_MAX_HITS
                            + " (default " + DEFAULT_MAX_HITS + ").")),
            EnumSet.of(ToolCapability.LEXICAL_SEARCH));

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        int maxHits;
        try {
            maxHits = parseMaxHits(invocation.argument(MAX_HITS));
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(ToolFailureReason.INVALID_ARGUMENTS, e.getMessage());
        }
        SearchOutcome outcome = context.lexicalSearch().search(invocation.argument(QUERY).trim(), maxHits);
        if (!outcome.isAvailable()) {
            return ToolResult.failure(ToolFailureReason.UNAVAILABLE, outcome.unavailableMessage());
        }
        List<Map<String, String>> records = new ArrayList<Map<String, String>>(outcome.hits().size());
        for (SearchHit hit : outcome.hits()) {
            Map<String, String> record = new LinkedHashMap<String, String>();
            record.put("uri", hit.resourceRef().uri().toString());
            record.put("kind", hit.resourceRef().kind().name());
            record.put("title", hit.title());
            record.put("chunk", String.valueOf(hit.chunkIndex()));
            record.put("score", String.valueOf(hit.score()));
            record.put("excerpt", hit.excerpt());
            records.add(record);
        }
        return ToolResult.success(records.size() + " hit(s)", records);
    }

    private static int parseMaxHits(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_MAX_HITS;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(MAX_HITS + " must be an integer: " + value);
        }
        if (parsed < 1 || parsed > LIMIT_MAX_HITS) {
            throw new IllegalArgumentException(MAX_HITS + " must be between 1 and " + LIMIT_MAX_HITS + ": " + value);
        }
        return parsed;
    }
}
