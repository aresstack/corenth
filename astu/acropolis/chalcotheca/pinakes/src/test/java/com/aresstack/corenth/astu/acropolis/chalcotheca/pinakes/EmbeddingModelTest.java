package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingModelTest {

    @Test
    void rejectsBlankIdAndNonPositiveDimension() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingModel(null, 3));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingModel("  ", 3));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingModel("m", 0));
    }

    @Test
    void acceptsOnlyVectorsOfItsDimension() {
        EmbeddingModel model = new EmbeddingModel("m", 2);
        assertDoesNotThrow(() -> model.requireCompatible(TestRefs.vector(1f, 2f)));
        assertThrows(IllegalArgumentException.class, () -> model.requireCompatible(TestRefs.vector(1f, 2f, 3f)));
        assertThrows(IllegalArgumentException.class, () -> model.requireCompatible(null));
    }

    @Test
    void distinguishesModelsWithSameDimension() {
        assertEquals(new EmbeddingModel("a", 2), new EmbeddingModel("a", 2));
        assertNotEquals(new EmbeddingModel("a", 2), new EmbeddingModel("b", 2));
        assertNotEquals(new EmbeddingModel("a", 2), new EmbeddingModel("a", 3));
    }
}
