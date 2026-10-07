package com.aresstack.corenth.proasteion.katagogion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, typed outcome of a tool invocation.
 *
 * <p>A success carries a human-readable text and optional structured records made of string
 * fields only, which keeps the result trivially serialisable for later transport adapters.
 * A failure carries a {@link ToolFailureReason} and a message, never a payload.
 */
public final class ToolResult {

    private final ToolFailureReason failureReason;
    private final String text;
    private final List<Map<String, String>> records;

    private ToolResult(ToolFailureReason failureReason, String text, List<Map<String, String>> records) {
        this.failureReason = failureReason;
        this.text = Names.nullToEmpty(text);
        this.records = records;
    }

    /** Create a successful result with text only. */
    public static ToolResult success(String text) {
        return new ToolResult(null, text, Collections.<Map<String, String>>emptyList());
    }

    /** Create a successful result with text and structured records. */
    public static ToolResult success(String text, List<Map<String, String>> records) {
        return new ToolResult(null, text, copyRecords(records));
    }

    /** Create a failed result. */
    public static ToolResult failure(ToolFailureReason reason, String message) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        return new ToolResult(reason, message, Collections.<Map<String, String>>emptyList());
    }

    public boolean isSuccess() {
        return failureReason == null;
    }

    /** Return the failure reason, or {@code null} for a success. */
    public ToolFailureReason failureReason() {
        return failureReason;
    }

    /** Return the result text for a success or the failure message otherwise. */
    public String text() {
        return text;
    }

    /** Return the structured records; always empty for a failure. */
    public List<Map<String, String>> records() {
        return records;
    }

    private static List<Map<String, String>> copyRecords(List<Map<String, String>> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> copy = new ArrayList<Map<String, String>>(records.size());
        for (Map<String, String> record : records) {
            if (record == null) {
                throw new IllegalArgumentException("records must not contain null");
            }
            Map<String, String> fields = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> field : record.entrySet()) {
                if (field.getKey() == null || field.getValue() == null) {
                    throw new IllegalArgumentException("record field names and values must not be null");
                }
                fields.put(field.getKey(), field.getValue());
            }
            copy.add(Collections.unmodifiableMap(fields));
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public String toString() {
        return isSuccess()
                ? "ToolResult{SUCCESS, records=" + records.size() + "}"
                : "ToolResult{" + failureReason + ", " + text + "}";
    }
}
