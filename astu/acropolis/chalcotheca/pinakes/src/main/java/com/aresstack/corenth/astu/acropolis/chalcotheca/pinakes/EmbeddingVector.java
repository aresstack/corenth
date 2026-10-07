package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.Arrays;

/**
 * Hold one immutable, finite embedding vector.
 *
 * <p>The vector rejects empty input and every {@code NaN} or infinite component, so that a
 * broken embedding runtime fails at the boundary instead of silently corrupting similarity
 * scores. A zero vector is a valid value but has no direction; check {@link #isZero()}
 * before using it for similarity.
 */
public final class EmbeddingVector {

    private final float[] values;

    public EmbeddingVector(float[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Embedding vector must not be null or empty");
        }
        for (int i = 0; i < values.length; i++) {
            if (Float.isNaN(values[i]) || Float.isInfinite(values[i])) {
                throw new IllegalArgumentException("Embedding vector component " + i + " is not finite");
            }
        }
        this.values = Arrays.copyOf(values, values.length);
    }

    /** Return the number of components. */
    public int dimension() {
        return values.length;
    }

    /** Return a defensive copy of the components. */
    public float[] copyValues() {
        return Arrays.copyOf(values, values.length);
    }

    /** Return {@code true} if every component is zero. */
    public boolean isZero() {
        for (float value : values) {
            if (value != 0f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compute the cosine similarity in {@code [-1, 1]}.
     *
     * @throws IllegalArgumentException if the dimensions differ or either vector is zero
     */
    public double cosineSimilarity(EmbeddingVector other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        if (other.values.length != values.length) {
            throw new IllegalArgumentException("Dimension mismatch: " + values.length + " vs " + other.values.length);
        }
        double dot = 0d;
        double normA = 0d;
        double normB = 0d;
        for (int i = 0; i < values.length; i++) {
            double a = values[i];
            double b = other.values[i];
            dot += a * b;
            normA += a * a;
            normB += b * b;
        }
        if (normA == 0d || normB == 0d) {
            throw new IllegalArgumentException("Cosine similarity is undefined for a zero vector");
        }
        double cosine = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return Math.max(-1d, Math.min(1d, cosine));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmbeddingVector)) {
            return false;
        }
        return Arrays.equals(values, ((EmbeddingVector) o).values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "EmbeddingVector{dimension=" + values.length + "}";
    }
}
