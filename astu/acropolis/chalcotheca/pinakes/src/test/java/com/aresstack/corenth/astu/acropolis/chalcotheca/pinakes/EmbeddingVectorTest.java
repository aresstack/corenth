package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingVectorTest {

    @Test
    void rejectsNullAndEmptyValues() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(null));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(new float[0]));
    }

    @Test
    void rejectsNonFiniteComponents() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(new float[]{1f, Float.NaN}));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(new float[]{Float.POSITIVE_INFINITY}));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(new float[]{0f, Float.NEGATIVE_INFINITY}));
    }

    @Test
    void copiesValuesDefensively() {
        float[] input = {1f, 2f, 3f};
        EmbeddingVector vector = new EmbeddingVector(input);
        input[0] = 99f;
        float[] copy = vector.copyValues();
        copy[1] = 99f;

        assertArrayEquals(new float[]{1f, 2f, 3f}, vector.copyValues());
        assertEquals(3, vector.dimension());
    }

    @Test
    void detectsZeroVectors() {
        assertTrue(new EmbeddingVector(new float[]{0f, 0f}).isZero());
        assertTrue(new EmbeddingVector(new float[]{-0f, 0f}).isZero());
        assertFalse(new EmbeddingVector(new float[]{0f, 0.001f}).isZero());
    }

    @Test
    void computesCosineSimilarity() {
        EmbeddingVector x = TestRefs.vector(1f, 0f);
        assertEquals(1d, x.cosineSimilarity(TestRefs.vector(3f, 0f)), 1e-12);
        assertEquals(0d, x.cosineSimilarity(TestRefs.vector(0f, 2f)), 1e-12);
        assertEquals(-1d, x.cosineSimilarity(TestRefs.vector(-1f, 0f)), 1e-12);
        assertEquals(Math.sqrt(0.5d), x.cosineSimilarity(TestRefs.vector(1f, 1f)), 1e-12);
    }

    @Test
    void rejectsCosineAcrossDimensionsOrWithZeroVector() {
        EmbeddingVector x = TestRefs.vector(1f, 0f);
        assertThrows(IllegalArgumentException.class, () -> x.cosineSimilarity(TestRefs.vector(1f, 0f, 0f)));
        assertThrows(IllegalArgumentException.class, () -> x.cosineSimilarity(TestRefs.vector(0f, 0f)));
        assertThrows(IllegalArgumentException.class, () -> TestRefs.vector(0f, 0f).cosineSimilarity(x));
        assertThrows(IllegalArgumentException.class, () -> x.cosineSimilarity(null));
    }

    @Test
    void comparesByValue() {
        assertEquals(TestRefs.vector(1f, 2f), TestRefs.vector(1f, 2f));
        assertEquals(TestRefs.vector(1f, 2f).hashCode(), TestRefs.vector(1f, 2f).hashCode());
        assertNotEquals(TestRefs.vector(1f, 2f), TestRefs.vector(2f, 1f));
    }
}
