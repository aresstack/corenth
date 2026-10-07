package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Name the vector space an embedding belongs to.
 *
 * <p>Vectors are only comparable when the same model produced them. A semantic index is
 * bound to exactly one model, and retrieval refuses to search it with a query vector from
 * another model even if the dimensions happen to match. The identifier is an opaque,
 * adapter-chosen name (for example a catalogue id); it carries no runtime type.
 */
public final class EmbeddingModel {

    private final String id;
    private final int dimension;

    public EmbeddingModel(String id, int dimension) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Embedding model id must not be null or blank");
        }
        if (dimension < 1) {
            throw new IllegalArgumentException("Embedding dimension must be at least 1");
        }
        this.id = id;
        this.dimension = dimension;
    }

    /** Return the opaque model identifier. */
    public String id() {
        return id;
    }

    /** Return the dimension of every vector of this model. */
    public int dimension() {
        return dimension;
    }

    /** Reject a vector that cannot belong to this model. */
    public void requireCompatible(EmbeddingVector vector) {
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }
        if (vector.dimension() != dimension) {
            throw new IllegalArgumentException("Vector dimension " + vector.dimension()
                    + " does not match model '" + id + "' dimension " + dimension);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmbeddingModel)) {
            return false;
        }
        EmbeddingModel that = (EmbeddingModel) o;
        return dimension == that.dimension && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + dimension;
    }

    @Override
    public String toString() {
        return "EmbeddingModel{" + id + ", dimension=" + dimension + "}";
    }
}
