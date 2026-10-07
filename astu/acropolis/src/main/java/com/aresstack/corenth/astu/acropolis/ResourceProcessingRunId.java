package com.aresstack.corenth.astu.acropolis;

/**
 * Identifier of one resource processing run (#10 Slice 4). Immutable value.
 */
public final class ResourceProcessingRunId {

    private final String value;

    public ResourceProcessingRunId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("run id must not be empty");
        }
        this.value = value;
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ResourceProcessingRunId && value.equals(((ResourceProcessingRunId) o).value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
