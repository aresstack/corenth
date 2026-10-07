package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.List;

/**
 * Port for turning texts into embedding vectors.
 *
 * <p>The port is synchronous and batch-capable; threading and scheduling belong to the
 * caller. Concrete runtimes (a local model sidecar, an HTTP service) are adapters outside
 * Pinakes. No such runtime is mandatory: retrieval works without any embedding client.
 */
public interface EmbeddingClient {

    /** Return the model whose vector space this client produces. */
    EmbeddingModel model();

    /**
     * Embed the given texts.
     *
     * @param purpose whether the texts are queries or passages
     * @param texts   the texts to embed; must not be empty
     * @return exactly one vector per text, in input order, each of {@link #model()} dimension
     * @throws EmbeddingException if the runtime fails or is unavailable
     */
    List<EmbeddingVector> embed(EmbeddingPurpose purpose, List<String> texts) throws EmbeddingException;
}
